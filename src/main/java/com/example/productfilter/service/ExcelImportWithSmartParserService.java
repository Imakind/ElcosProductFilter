package com.example.productfilter.service;

import com.example.productfilter.model.*;
import com.example.productfilter.repository.*;
import com.example.productfilter.service.SmartProductParser.ParsedParams;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ExcelImportWithSmartParserService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final SupplierRepository supplierRepository;
    private final ProductParameterRepository parameterRepository;
    private final CategoryRepository categoryRepository;
    private final ProductCategoriesRepository productCategoriesRepository;

    public ExcelImportWithSmartParserService(ProductRepository productRepository,
                                             BrandRepository brandRepository,
                                             SupplierRepository supplierRepository,
                                             ProductParameterRepository parameterRepository,
                                             CategoryRepository categoryRepository,
                                             ProductCategoriesRepository productCategoriesRepository) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.supplierRepository = supplierRepository;
        this.parameterRepository = parameterRepository;
        this.categoryRepository = categoryRepository;
        this.productCategoriesRepository = productCategoriesRepository;
    }

    public void importFromExcel(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String article = getString(row, 0);
                String name = getString(row, 1);
                Double price = getDouble(row, 2);
                String brandName = getString(row, 3);
                String supplierName = getString(row, 4);
                String groupName = getString(row, 5);
                String subGroupName = getString(row, 6);

                if (article == null || name == null || name.isBlank()) continue;

                // === Бренд
                Brand brand = brandRepository.findByBrandNameIgnoreCase(brandName)
                        .orElseGet(() -> {
                            Brand b = new Brand();
                            b.setBrandName(brandName);
                            return brandRepository.save(b);
                        });

                // === Поставщик
                Supplier supplier = supplierRepository.findByNameIgnoreCase(supplierName)
                        .orElseGet(() -> {
                            Supplier s = new Supplier();
                            s.setName(supplierName);
                            return supplierRepository.save(s);
                        });

                // === Найти или создать товар
                Optional<Product> existingOpt = productRepository.findByArticleCode(article);
                Product product = existingOpt.orElseGet(Product::new);

                product.setArticleCode(article);
                product.setName(name);
                product.setPrice(price);
                product.setBrand(brand);
                product.setSupplier(supplier);
                product.setImportedAt(LocalDateTime.now());

                product = productRepository.save(product);

                // === Очистить старые параметры
                parameterRepository.deleteByProduct_ProductId(product.getProductId());

                // === Записать новые
                ParsedParams parsed = SmartProductParser.parse(name);
                ProductParameters param = new ProductParameters();
                param.setProduct(product);
                param.setParam1(parsed.param1);
                param.setParam2(parsed.param2);
                param.setParam3(parsed.param3);
                param.setParam4(parsed.param4);
                param.setParam5(parsed.param5);
                parameterRepository.save(param);

                // === Категории (группа и подгруппа)
                Category group = null;
                Integer groupId = null;

                if (groupName != null && !groupName.isBlank()) {
                    group = categoryRepository.findByNameIgnoreCase(groupName)
                            .orElseGet(() -> {
                                Category c = new Category();
                                c.setName(groupName);
                                return categoryRepository.save(c);
                            });

                    groupId = group.getCategoryId();

                    // Связь с группой
                    ProductCategories pc = new ProductCategories();
                    pc.setProductId(product.getProductId());
                    pc.setCategoryId(groupId);
                    productCategoriesRepository.save(pc);
                }

                if (subGroupName != null && !subGroupName.isBlank()) {
                    Integer finalGroupId = groupId;  // теперь можно использовать в лямбде
                    Category subGroup = categoryRepository.findByNameIgnoreCase(subGroupName)
                            .orElseGet(() -> {
                                Category c = new Category();
                                c.setName(subGroupName);
                                if (finalGroupId != null) c.setParentCategoryId(finalGroupId);
                                return categoryRepository.save(c);
                            });

                    // Связь с подгруппой
                    ProductCategories pc = new ProductCategories();
                    pc.setProductId(product.getProductId());
                    pc.setCategoryId(subGroup.getCategoryId());
                    productCategoriesRepository.save(pc);
                }
            }
        }
    }

    private String getString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
            default -> null;
        };
    }

    private Double getDouble(Row row, int col) {
        try {
            Cell cell = row.getCell(col);
            if (cell == null || cell.getCellType() != CellType.NUMERIC) return null;
            return cell.getNumericCellValue();
        } catch (Exception e) {
            return null;
        }
    }
}
