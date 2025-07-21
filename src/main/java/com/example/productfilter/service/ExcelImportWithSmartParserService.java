package com.example.productfilter.service;

import com.example.productfilter.model.*;
import com.example.productfilter.repository.*;
import com.example.productfilter.service.SmartProductParser.ParsedParams;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

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

    @Transactional
    public void importFromExcel(File file) throws Exception {
        try (InputStream is = new FileInputStream(file); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);

            // Кэши
            Map<String, Brand> brandCache = new HashMap<>();
            Map<String, Supplier> supplierCache = new HashMap<>();
            Map<String, Category> categoryCache = new HashMap<>();

            int totalRows = sheet.getLastRowNum();
            int importedCount = 0;
            int updatedCount = 0;
            int createdBrands = 0;
            int createdSuppliers = 0;

            System.out.println("📥 Импорт начат. Всего строк: " + totalRows);

            for (int i = 1; i <= totalRows; i++) {
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
                    brand = brandCache.get(brandName.toLowerCase());
                    if (brand == null) {
                        brand = brandRepository.findByBrandNameIgnoreCase(brandName).orElse(null);
                        if (brand == null) {
                            brand = new Brand(brandName);
                            brand = brandRepository.save(brand);
                            createdBrands++;
                        }
                        brandCache.put(brandName.toLowerCase(), brand);
                    }
                }

                // === Поставщик
                Supplier supplier = null;
                if (supplierName != null && !supplierName.isBlank()) {
                    supplier = supplierCache.get(supplierName.toLowerCase());
                    if (supplier == null) {
                        supplier = supplierRepository.findByNameIgnoreCase(supplierName).orElse(null);
                        if (supplier == null) {
                            supplier = new Supplier(supplierName);
                            supplier = supplierRepository.save(supplier);
                            createdSuppliers++;
                        }
                        supplierCache.put(supplierName.toLowerCase(), supplier);
                    }
                }

                if (supplier == null) continue;

                // === Товар
                Product product = productRepository.findByArticleCodeAndSupplier_SupplierId(article, supplier.getSupplierId())
                        .orElse(null);

                boolean isNew = false;
                if (product == null) {
                    product = new Product();
                    isNew = true;
                }

                product.setArticleCode(article);
                product.setName(name);
                product.setPrice(price);
                product.setBrand(brand);
                product.setSupplier(supplier);
                product.setImportedAt(LocalDateTime.now());
                product.setImportPriceDate(importPriceDate != null ? importPriceDate.trim() : null);

                product = productRepository.save(product);
                if (isNew) importedCount++; else updatedCount++;

                // === Параметры
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

                // === Категории
                Category group = null;
                Integer groupId = null;

                if (groupName != null && !groupName.isBlank()) {
                    group = getOrCreateCategory(groupName.trim(), null, categoryCache);
                    groupId = group.getCategoryId();

                    if (!productCategoriesRepository.existsByProductIdAndCategoryId(product.getProductId(), groupId)) {
                        ProductCategories pc = new ProductCategories();
                        pc.setProductId(product.getProductId());
                        pc.setCategoryId(groupId);
                        productCategoriesRepository.save(pc);
                    }
                }

                if (subGroupName != null && !subGroupName.isBlank()) {
                    Category subGroup = getOrCreateCategory(subGroupName.trim(), groupId, categoryCache);
                    if (!productCategoriesRepository.existsByProductIdAndCategoryId(product.getProductId(), subGroup.getCategoryId())) {
                        ProductCategories pc = new ProductCategories();
                        pc.setProductId(product.getProductId());
                        pc.setCategoryId(subGroup.getCategoryId());
                        productCategoriesRepository.save(pc);
                    }
                }

                if (i % 100 == 0) {
                    System.out.println("✔ Обработано " + i + " / " + totalRows);
                }
            }

            System.out.println("✅ Импорт завершён:");
            System.out.println("    Новых товаров: " + importedCount);
            System.out.println("    Обновлено товаров: " + updatedCount);
            System.out.println("    Новых брендов: " + createdBrands);
            System.out.println("    Новых поставщиков: " + createdSuppliers);
        }
    }

    private Category getOrCreateCategory(String name, Integer parentId, Map<String, Category> cache) {
        String key = name.toLowerCase();
        if (cache.containsKey(key)) return cache.get(key);

        Category cat = categoryRepository.findByNameIgnoreCase(name).orElse(null);
        if (cat == null) {
            cat = new Category();
            cat.setName(name);
            cat.setParentCategoryId(parentId);
            cat = categoryRepository.save(cat);
        }
        cache.put(key, cat);
        return cat;
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
