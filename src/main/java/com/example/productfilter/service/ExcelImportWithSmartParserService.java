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
import java.time.format.DateTimeFormatter;

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
                String importPriceDate = null;
                Cell importDateCell = row.getCell(7);
                if (importDateCell != null && importDateCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(importDateCell)) {
                    importPriceDate = importDateCell.getLocalDateTimeCellValue().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                } else if (importDateCell != null && importDateCell.getCellType() == CellType.STRING) {
                    importPriceDate = importDateCell.getStringCellValue().trim();
                }


                if (article == null || name == null || name.isBlank()) continue;

                // === Бренд
                Brand brand = null;
                if (brandName != null && !brandName.isBlank()) {
                    brand = brandRepository.findByBrandNameIgnoreCase(brandName)
                            .orElseGet(() -> brandRepository.save(new Brand(brandName)));
                }

                // === Поставщик
                Supplier supplier = null;
                if (supplierName != null && !supplierName.isBlank()) {
                    supplier = supplierRepository.findByNameIgnoreCase(supplierName)
                            .orElseGet(() -> supplierRepository.save(new Supplier(supplierName)));
                }

                if (supplier == null) continue; // Без поставщика — не импортируем

                // === Найти или создать товар
                Product product = productRepository.findByArticleCodeAndSupplier_SupplierId(article, supplier.getSupplierId())
                        .orElse(new Product());

                product.setArticleCode(article);
                product.setName(name);
                product.setPrice(price);
                product.setBrand(brand);
                product.setSupplier(supplier);
                product.setImportedAt(LocalDateTime.now());

                // === Устанавливаем importPriceDate как строку (формат из Excel, например "17.04.2025")
                if (importPriceDate != null && !importPriceDate.isBlank()) {
                    product.setImportPriceDate(importPriceDate.trim());
                } else {
                    product.setImportPriceDate(null);
                }

                product = productRepository.save(product);

                // === Очистить и записать параметры
                parameterRepository.deleteByProduct_ProductId(product.getProductId());

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

                // === ГРУППА
                if (groupName != null && !groupName.isBlank()) {
                    group = categoryRepository.findByNameIgnoreCase(groupName.trim())
                            .orElseGet(() -> {
                                Category c = new Category();
                                c.setName(groupName.trim());
                                return categoryRepository.save(c);
                            });

                    groupId = group.getCategoryId();

                    if (!productCategoriesRepository.existsByProductIdAndCategoryId(product.getProductId(), groupId)) {
                        ProductCategories pc = new ProductCategories();
                        pc.setProductId(product.getProductId());
                        pc.setCategoryId(groupId);
                        productCategoriesRepository.save(pc);
                    }
                }

                // === ПОДГРУППА
                if (subGroupName != null && !subGroupName.isBlank()) {
                    Integer finalGroupId = groupId;
                    Category subGroup = categoryRepository.findByNameIgnoreCase(subGroupName.trim())
                            .orElseGet(() -> {
                                Category c = new Category();
                                c.setName(subGroupName.trim());
                                if (finalGroupId != null) {
                                    c.setParentCategoryId(finalGroupId);
                                }
                                return categoryRepository.save(c);
                            });

                    if (!productCategoriesRepository.existsByProductIdAndCategoryId(product.getProductId(), subGroup.getCategoryId())) {
                        ProductCategories pc = new ProductCategories();
                        pc.setProductId(product.getProductId());
                        pc.setCategoryId(subGroup.getCategoryId());
                        productCategoriesRepository.save(pc);
                    }
                }
            }
        }
    }

    private String getString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                yield d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private Double getDouble(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING -> {
                try {
                    yield Double.parseDouble(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }
}
