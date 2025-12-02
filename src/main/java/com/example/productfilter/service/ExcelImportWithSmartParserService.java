package com.example.productfilter.service;

import com.example.productfilter.model.*;
import com.example.productfilter.repository.*;
import com.example.productfilter.service.SmartProductParser.ParsedParams;
import com.example.productfilter.service.impl.BrandService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
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
    private final BrandService brandService;


    public ExcelImportWithSmartParserService(ProductRepository productRepository,
                                             BrandRepository brandRepository,
                                             SupplierRepository supplierRepository,
                                             ProductParameterRepository parameterRepository,
                                             CategoryRepository categoryRepository,
                                             ProductCategoriesRepository productCategoriesRepository,
                                             BrandService brandService) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.supplierRepository = supplierRepository;
        this.parameterRepository = parameterRepository;
        this.categoryRepository = categoryRepository;
        this.productCategoriesRepository = productCategoriesRepository;
        this.brandService = brandService;
    }

    // Какие поля ищем в Excel
    private enum Field {
        ARTICLE,
        NAME,
        PRICE,
        BRAND,
        SUPPLIER,
        GROUP,
        SUBGROUP,
        IMPORT_DATE
    }

    // Ключевые слова/синонимы для поиска колонок по заголовкам
    private static final Map<Field, List<String>> KEYWORDS = Map.of(
            Field.ARTICLE,     List.of("артикул", "article", "код", "code", "sku"),
            Field.NAME,        List.of("наименование", "название", "товар", "name", "product"),
            Field.PRICE,       List.of("цена", "price", "стоимость", "base price"),
            Field.BRAND,       List.of("бренд", "brand", "марка", "производитель"),
            Field.SUPPLIER,    List.of("поставщик", "supplier", "постав.", "контрагент"),
            Field.GROUP,       List.of("группа", "категория", "category", "раздел"),
            Field.SUBGROUP,    List.of("подгруппа", "subgroup", "подкатегория", "subcategory"),
            Field.IMPORT_DATE, List.of("дата", "import date", "дата цены", "price date")
    );

    @Transactional
    public void importFromExcel(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream(); Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() < 1) return;

            // 1) Строим карту "поле -> индекс колонки" по заголовку
            Row header = sheet.getRow(0);
            if (header == null) {
                throw new IllegalArgumentException("В Excel отсутствует строка заголовков (первая строка).");
            }
            Map<Field, Integer> colMap = buildColumnMap(header);

            // обязательные колонки
            require(colMap, Field.ARTICLE, Field.NAME, Field.SUPPLIER);

            // 2) Парсим строки данных
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                String article = getString(row, colMap.get(Field.ARTICLE));
                String name = getString(row, colMap.get(Field.NAME));
                BigDecimal price = getBigDecimal(row, colMap.get(Field.PRICE));
                String brandName = getString(row, colMap.get(Field.BRAND));
                String supplierName = getString(row, colMap.get(Field.SUPPLIER));
                String groupName = getString(row, colMap.get(Field.GROUP));
                String subGroupName = getString(row, colMap.get(Field.SUBGROUP));

                String importPriceDate = getDateAsString(row, colMap.get(Field.IMPORT_DATE));

                if (article == null || name == null || name.isBlank()) continue;

                // === Бренд
                Brand brand = null;
                if (brandName != null && !brandName.isBlank()) {
                    brand = brandService.getOrCreateBrand(brandName.trim());
                }

                // === Поставщик
                Supplier supplier = null;
                if (supplierName != null && !supplierName.isBlank()) {
                    supplier = supplierRepository.findByNameIgnoreCase(supplierName.trim())
                            .orElseGet(() -> supplierRepository.save(new Supplier(supplierName.trim())));
                }

                if (supplier == null) continue; // Без поставщика — не импортируем

                // === Найти или создать товар
                Product product = productRepository
                        .findByArticleCodeAndSupplier_SupplierId(article, supplier.getSupplierId())
                        .orElse(new Product());

                product.setArticleCode(article);
                product.setName(name);
                product.setPrice(price);
                product.setBrand(brand);
                product.setSupplier(supplier);
                product.setImportedAt(LocalDateTime.now());

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

    // -------------------- Header mapping --------------------

    private Map<Field, Integer> buildColumnMap(Row header) {
        Map<Field, Integer> map = new EnumMap<>(Field.class);

        // нормализованные заголовки -> индекс
        Map<String, Integer> headerIndex = new HashMap<>();
        for (Cell cell : header) {
            String h = getCellAsString(cell);
            if (h == null) continue;
            headerIndex.put(normalize(h), cell.getColumnIndex());
        }

        // поиск по ключевым словам
        for (Field f : Field.values()) {
            Integer idx = findByKeywords(headerIndex, KEYWORDS.get(f));
            if (idx != null) map.put(f, idx);
        }

        return map;
    }

    private Integer findByKeywords(Map<String, Integer> headerIndex, List<String> keywords) {
        if (keywords == null) return null;

        for (Map.Entry<String, Integer> entry : headerIndex.entrySet()) {
            String headerNorm = entry.getKey();
            for (String kw : keywords) {
                String kwNorm = normalize(kw);
                if (headerNorm.contains(kwNorm)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private void require(Map<Field, Integer> map, Field... required) {
        List<String> missing = new ArrayList<>();
        for (Field f : required) {
            if (!map.containsKey(f)) {
                missing.add(f.name());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "В Excel не найдены обязательные колонки по ключевым словам: " + missing +
                            ". Проверь заголовки первой строки."
            );
        }
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase()
                .replace("ё", "е")
                .replaceAll("[^a-zа-я0-9]+", " ")
                .trim();
    }

    // -------------------- Cell readers --------------------

    private String getCellAsString(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                yield d == Math.floor(d) ? String.valueOf((int) d) : String.valueOf(d);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> null;
        };
    }

    private String getString(Row row, Integer col) {
        if (col == null) return null;
        Cell cell = row.getCell(col);
        return getCellAsString(cell);
    }

    private BigDecimal getBigDecimal(Row row, Integer col) {
        if (col == null) return null;

        Cell cell = row.getCell(col);
        if (cell == null) return null;

        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING -> {
                try {
                    String raw = cell.getStringCellValue()
                            .trim()
                            .replace(" ", "")
                            .replace(",", ".");
                    yield new BigDecimal(raw);
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            case FORMULA -> {
                try {
                    yield BigDecimal.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    private String getDateAsString(Row row, Integer col) {
        if (col == null) return null;

        Cell cell = row.getCell(col);
        if (cell == null) return null;

        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue()
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        }
        if (cell.getCellType() == CellType.STRING) {
            return cell.getStringCellValue().trim();
        }
        if (cell.getCellType() == CellType.FORMULA) {
            try {
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue()
                            .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
                }
                return cell.getStringCellValue().trim();
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}
