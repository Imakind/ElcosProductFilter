package com.example.productfilter.controller;

import com.example.productfilter.model.*;
import com.example.productfilter.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.PdfPTable;


import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;



@Controller
public class ProductFilterController {
    @Autowired
    private BrandRepository brandRepo;
    @Autowired private CategoryRepository categoryRepo;
    @Autowired private ProductRepository productRepo;
    @Autowired private ProductParameterRepository parameterRepo;
    @Autowired
    private ProductCategoriesRepository productCategoriesRepo;


    @GetMapping("/")
    public String index(Model model, HttpSession session) {
        model.addAttribute("brands", brandRepo.findAll());
        model.addAttribute("groups", categoryRepo.findByParentCategoryIdIsNull());
        model.addAttribute("subGroups", List.of());
        model.addAttribute("param1List", parameterRepo.findDistinctParam1());
        model.addAttribute("param2List", parameterRepo.findDistinctParam2());
        model.addAttribute("param3List", parameterRepo.findDistinctParam3());
        model.addAttribute("param4List", parameterRepo.findDistinctParam4());
        model.addAttribute("param5List", parameterRepo.findDistinctParam5());

        model.addAttribute("filterParams", new HashMap<String, Object>());

        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("cart");
        model.addAttribute("cartCount", cart != null ? cart.values().stream().mapToInt(i -> i).sum() : 0);


        return "filter";
    }

    @GetMapping("/filter/options")
    @ResponseBody
    public Map<String, Object> getOptionsByBrand(@RequestParam("brandId") Integer brandId) {
        Map<String, Object> response = new HashMap<>();

        List<Product> products = productRepo.findByBrand_BrandId(brandId);

        // Категории этих продуктов
        Set<Integer> productIds = products.stream().map(Product::getProductId).collect(Collectors.toSet());
        List<Category> allCategories = categoryRepo.findByProducts(productIds);

        List<Category> parentCategories = allCategories.stream()
                .filter(c -> c.getParentCategoryId() == null)
                .collect(Collectors.toList());


        // Параметры этих продуктов
        List<ProductParameters> params = parameterRepo.findByProduct_ProductIdIn(productIds);
        Set<String> param1Set = params.stream().map(ProductParameters::getParam1).filter(Objects::nonNull).collect(Collectors.toSet());

        response.put("groups", parentCategories);
        response.put("param1List", param1Set);

        return response;
    }


    @GetMapping("/filter/groups")
    @ResponseBody
    public List<Category> getGroupsByBrand(@RequestParam("brandId") Integer brandId) {
        List<Product> products = productRepo.findByBrand_BrandId(brandId);
        Set<Integer> productIds = products.stream().map(Product::getProductId).collect(Collectors.toSet());
        return categoryRepo.findParentCategoriesByProducts(productIds);
    }

    @GetMapping("/filter/subgroups")
    @ResponseBody
    public List<Category> getSubGroups(@RequestParam("groupId") Integer groupId) {
        return categoryRepo.findByParentCategoryId(groupId);
    }

    @GetMapping("/filter/parameters")
    @ResponseBody
    public Map<String, Set<String>> getParameters(
            @RequestParam("brandId") Integer brandId,
            @RequestParam(value = "groupId", required = false) Integer groupId,
            @RequestParam(value = "subGroupId", required = false) Integer subGroupId) {

        List<Product> products = productRepo.findByBrand_BrandId(brandId);

        if (groupId != null) {
            Set<Integer> matchingProductIds = new HashSet<>();
            List<ProductCategories> allRelations = productCategoriesRepo.findAll();

            for (ProductCategories pc : allRelations) {
                if (groupId.equals(pc.getCategoryId())) {
                    matchingProductIds.add(pc.getProductId());
                }
            }

            products = products.stream()
                    .filter(p -> matchingProductIds.contains(p.getProductId()))
                    .collect(Collectors.toList());
        }

        if (subGroupId != null) {
            Set<Integer> matchingProductIds = new HashSet<>();
            List<ProductCategories> allRelations = productCategoriesRepo.findAll();

            for (ProductCategories pc : allRelations) {
                if (subGroupId.equals(pc.getCategoryId())) {
                    matchingProductIds.add(pc.getProductId());
                }
            }

            products = products.stream()
                    .filter(p -> matchingProductIds.contains(p.getProductId()))
                    .collect(Collectors.toList());
        }

        Set<Integer> productIds = products.stream().map(Product::getProductId).collect(Collectors.toSet());
        List<ProductParameters> params = parameterRepo.findByProduct_ProductIdIn(productIds);

        Map<String, Set<String>> paramMap = new HashMap<>();
        paramMap.put("param1List", params.stream().map(ProductParameters::getParam1).filter(Objects::nonNull).collect(Collectors.toSet()));
        paramMap.put("param2List", params.stream().map(ProductParameters::getParam2).filter(Objects::nonNull).collect(Collectors.toSet()));
        paramMap.put("param3List", params.stream().map(ProductParameters::getParam3).filter(Objects::nonNull).collect(Collectors.toSet()));
        paramMap.put("param4List", params.stream().map(ProductParameters::getParam4).filter(Objects::nonNull).collect(Collectors.toSet()));
        paramMap.put("param5List", params.stream().map(ProductParameters::getParam5).filter(Objects::nonNull).collect(Collectors.toSet()));

        return paramMap;
    }

    @GetMapping("/filter/results")
    public String filterResults(
            @RequestParam(value = "brandId", required = false) Integer brandId,
            @RequestParam(value = "groupId", required = false) Integer groupId,
            @RequestParam(value = "subGroupId", required = false) Integer subGroupId,
            @RequestParam(value = "param1", required = false) String param1,
            @RequestParam(value = "param2", required = false) String param2,
            @RequestParam(value = "param3", required = false) String param3,
            @RequestParam(value = "param4", required = false) String param4,
            @RequestParam(value = "param5", required = false) String param5,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", defaultValue = "0") int page,
            HttpServletRequest request,
            HttpSession session,
            Model model
    ) {
        int pageSize = 21;
        Pageable pageable = PageRequest.of(page, pageSize);

        // Загружаем все товары
        List<Product> products = productRepo.findAll();
        Set<Integer> productIds = products.stream()
                .map(Product::getProductId)
                .collect(Collectors.toSet());

        // Фильтрация по бренду
        if (brandId != null) {
            products = products.stream()
                    .filter(p -> p.getBrand().getBrandId().equals(brandId))
                    .collect(Collectors.toList());
            productIds = products.stream().map(Product::getProductId).collect(Collectors.toSet());
        }

        // -------------- Фильтрация по ключевому слову -----------------
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            products = products.stream()
                    .filter(p -> p.getName() != null && p.getName().toLowerCase().contains(lowerKeyword))
                    .collect(Collectors.toList());
        }


        // Фильтрация по группам/подгруппам
        if (groupId != null || subGroupId != null) {
            List<ProductCategories> links = productCategoriesRepo.findAll();
            if (groupId != null) {
                Set<Integer> groupMatches = links.stream()
                        .filter(pc -> groupId.equals(pc.getCategoryId()))
                        .map(ProductCategories::getProductId)
                        .collect(Collectors.toSet());
                productIds.retainAll(groupMatches);
            }
            if (subGroupId != null) {
                Set<Integer> subMatches = links.stream()
                        .filter(pc -> subGroupId.equals(pc.getCategoryId()))
                        .map(ProductCategories::getProductId)
                        .collect(Collectors.toSet());
                productIds.retainAll(subMatches);
            }
        }

        // Фильтрация по параметрам
        List<ProductParameters> params = parameterRepo.findByProduct_ProductIdIn(productIds);

        if (param1 != null && !param1.isEmpty()) {
            params = params.stream().filter(p -> param1.equals(p.getParam1())).collect(Collectors.toList());
        }
        if (param2 != null && !param2.isEmpty()) {
            params = params.stream().filter(p -> param2.equals(p.getParam2())).collect(Collectors.toList());
        }
        if (param3 != null && !param3.isEmpty()) {
            params = params.stream().filter(p -> param3.equals(p.getParam3())).collect(Collectors.toList());
        }
        if (param4 != null && !param4.isEmpty()) {
            params = params.stream().filter(p -> param4.equals(p.getParam4())).collect(Collectors.toList());
        }
        if (param5 != null && !param5.isEmpty()) {
            params = params.stream().filter(p -> param5.equals(p.getParam5())).collect(Collectors.toList());
        }

        Set<Integer> finalProductIds;
        if (!params.isEmpty()) {
            finalProductIds = params.stream()
                    .map(p -> p.getProduct().getProductId())
                    .collect(Collectors.toSet());
        } else {
            finalProductIds = productIds;
        }

        products = products.stream()
                .filter(p -> finalProductIds.contains(p.getProductId()))
                .collect(Collectors.toList());

        // --------------- Сортировка ----------------
        if (sort != null && !sort.isEmpty()) {
            String[] sorts = sort.split(",");

            for (String s : sorts) {
                switch (s) {
                    case "priceAsc":
                        products.sort(Comparator.comparing(Product::getPrice, Comparator.nullsLast(Double::compareTo)));
                        break;
                    case "priceDesc":
                        products.sort(Comparator.comparing(Product::getPrice, Comparator.nullsLast(Double::compareTo)).reversed());
                        break;
                    case "nameAsc":
                        products.sort(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER));
                        break;
                    case "nameDesc":
                        products.sort(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER).reversed());
                        break;
                    case "brandAsc":
                        products.sort(Comparator.comparing(p -> p.getBrand().getBrandName(), String.CASE_INSENSITIVE_ORDER));
                        break;
                    case "brandDesc":
                        products.sort(Comparator.comparing((Product p) -> p.getBrand().getBrandName(), String.CASE_INSENSITIVE_ORDER).reversed());
                        break;
                }
            }
        }


        // --------------- Пагинация вручную ----------------
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), products.size());
        List<Product> pageContent = start < end ? products.subList(start, end) : List.of();

        Page<Product> productPage = new org.springframework.data.domain.PageImpl<>(pageContent, pageable, products.size());

        model.addAttribute("products", productPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", productPage.getTotalPages());

        // Передаем все фильтры обратно на страницу
        model.addAttribute("brands", brandRepo.findAll());
        model.addAttribute("groups", categoryRepo.findByParentCategoryIdIsNull());
        model.addAttribute("subGroups", groupId != null ? categoryRepo.findByParentCategoryId(groupId) : List.of());
        model.addAttribute("param1List", parameterRepo.findDistinctParam1());
        model.addAttribute("param2List", parameterRepo.findDistinctParam2());
        model.addAttribute("param3List", parameterRepo.findDistinctParam3());
        model.addAttribute("param4List", parameterRepo.findDistinctParam4());
        model.addAttribute("param5List", parameterRepo.findDistinctParam5());

        Map<String, Object> selectedParams = new HashMap<>();
        selectedParams.put("brandId", brandId);
        selectedParams.put("groupId", groupId);
        selectedParams.put("subGroupId", subGroupId);
        selectedParams.put("param1", param1);
        selectedParams.put("param2", param2);
        selectedParams.put("param3", param3);
        selectedParams.put("param4", param4);
        selectedParams.put("param5", param5);
        selectedParams.put("sort", sort);
        model.addAttribute("filterParams", selectedParams);

        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("cart");
        model.addAttribute("cartCount", cart != null ? cart.values().stream().mapToInt(i -> i).sum() : 0);

        model.addAttribute("quantities", cart != null ? cart : new HashMap<>());


        session.setAttribute("lastFilters", selectedParams);


        // Проверяем AJAX запрос или обычный
        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return "fragments/product_list :: productList";
        }

        return "filter";
    }



    @PostMapping("/cart/add")
    @ResponseBody
    public void addToCart(@RequestParam("productId") Integer productId, HttpSession session) {
        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("cart");
        if (cart == null) cart = new HashMap<>();

        cart.put(productId, cart.getOrDefault(productId, 0) + 1);
        session.setAttribute("cart", cart);
    }

    @GetMapping("/cart")
    public String viewCart(Model model, HttpSession session) {
        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("cart");

        Map<Integer, Double> coefficientMap = (Map<Integer, Double>) session.getAttribute("coefficientMap");

        if (cart == null) {
            cart = new HashMap<>();
        }
        if (coefficientMap == null) {
            coefficientMap = new HashMap<>();
        }

        List<Product> products = (cart != null && !cart.isEmpty())
                ? productRepo.findAllById(cart.keySet())
                : List.of();

        // Параметры товаров
        List<ProductParameters> parameters = parameterRepo.findByProduct_ProductIdIn(
                products.stream().map(Product::getProductId).collect(Collectors.toSet())
        );

        model.addAttribute("cartProducts", products);
        model.addAttribute("cartParams", parameters);
        model.addAttribute("quantities", cart);
        model.addAttribute("coefficientMap", coefficientMap);

        //  Оптимизированная загрузка категорий
        Set<Integer> productIds = products.stream()
                .map(Product::getProductId)
                .collect(Collectors.toSet());

        List<ProductCategories> links = productCategoriesRepo.findByProductIdIn(productIds);
        Set<Integer> categoryIds = links.stream()
                .map(ProductCategories::getCategoryId)
                .collect(Collectors.toSet());

        List<Category> allCategories = categoryRepo.findAllById(categoryIds);
        Map<Integer, Category> categoryMap = allCategories.stream()
                .collect(Collectors.toMap(Category::getCategoryId, c -> c));

        Map<Integer, List<Category>> productIdToCategories = new HashMap<>();
        for (ProductCategories link : links) {
            Integer productId = link.getProductId();
            Integer categoryId = link.getCategoryId();

            Category category = categoryMap.get(categoryId);
            if (category != null) {
                productIdToCategories
                        .computeIfAbsent(productId, k -> new ArrayList<>())
                        .add(category);
            }
        }

        model.addAttribute("productCategoriesMap", productIdToCategories);

        //  Восстановим параметры фильтра (если надо вернуться)
        Map<String, Object> lastFilters = (Map<String, Object>) session.getAttribute("lastFilters");
        model.addAttribute("filterParams", lastFilters != null ? lastFilters : new HashMap<>());

        model.addAttribute("quantities", cart);

        double totalSum = 0.0;
        for (Product product : products) {
            int qty = cart.getOrDefault(product.getProductId(), 1);
            double price = product.getPrice() != null ? product.getPrice() : 0.0;
            double coeff = coefficientMap.getOrDefault(product.getProductId(), 1.0);
            totalSum += qty * price * coeff;
        }
        model.addAttribute("totalSum", totalSum);

        return "cart";
    }

    @PostMapping("/cart/coefficient")
    @ResponseBody
    public void updateCoefficient(@RequestParam("productId") Integer productId,
                                  @RequestParam("coefficient") Double coefficient,
                                  HttpSession session) {
        Map<Integer, Double> coefficientMap = (Map<Integer, Double>) session.getAttribute("coefficientMap");
        if (coefficientMap == null) {
            coefficientMap = new HashMap<>();
        }
        coefficientMap.put(productId, coefficient);
        session.setAttribute("coefficientMap", coefficientMap);
    }

    @PostMapping("/cart/remove")
    @ResponseBody
    public void removeFromCart(@RequestParam("productId") Integer productId, HttpSession session) {
        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("cart");
        if (cart != null) {
            int qty = cart.getOrDefault(productId, 0);
            if (qty > 1) {
                cart.put(productId, qty - 1);
            } else {
                cart.remove(productId);
            }
            session.setAttribute("cart", cart);
        }
    }


    @PostMapping("/cart/confirm")
    public String confirmCart(HttpSession session, Model model) {
        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("cart");

        if (cart == null || cart.isEmpty()) {
            return "redirect:/cart";
        }

        List<Product> products = productRepo.findAllById(cart.keySet());

        // 1. Считаем итоговую сумму
        double totalSum = 0.0;
        for (Product product : products) {
            int qty = cart.getOrDefault(product.getProductId(), 1);
            double price = product.getPrice() != null ? product.getPrice() : 0.0;
            totalSum += price * qty;
        }

        // 2. Параметры
        List<ProductParameters> parameters = parameterRepo.findByProduct_ProductIdIn(cart.keySet());
        Map<Integer, ProductParameters> paramMap = parameters.stream()
                .collect(Collectors.toMap(p -> p.getProduct().getProductId(), p -> p));

        model.addAttribute("products", products);
        model.addAttribute("paramMap", paramMap);
        model.addAttribute("quantities", cart);
        model.addAttribute("totalSum", totalSum);

        // 3. Сохраняем в сессию
        session.setAttribute("proposalCart", new HashMap<>(cart));
        session.setAttribute("proposalProducts", products);
        session.setAttribute("proposalTotal", totalSum);

        return "proposal";
    }


    @GetMapping("/proposal/pdf")
    public void downloadProposalPdf(HttpServletResponse response, HttpSession session) throws IOException {
        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("proposalCart");
        List<Product> products = (List<Product>) session.getAttribute("proposalProducts");
        Double totalSum = (Double) session.getAttribute("proposalTotal");

        if (cart == null || products == null || cart.isEmpty()) {
            response.sendRedirect("/cart");
            return;
        }



        // Преобразуем в сет именно Integer, иначе будет ошибка типов
        Set<Integer> productIds = new HashSet<>(cart.keySet());

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=proposal.pdf");

        try (OutputStream out = response.getOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            document.add(new Paragraph("\u041a\u043e\u043c\u043c\u0435\u0440\u0447\u0435\u0441\u043a\u043e\u0435 \u043f\u0440\u0435\u0434\u043b\u043e\u0436\u0435\u043d\u0438\u0435", titleFont));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);
            table.addCell("\u041d\u0430\u0438\u043c\u0435\u043d\u043e\u0432\u0430\u043d\u0438\u0435");
            table.addCell("\u041a\u043e\u043b\u0438\u0447\u0435\u0441\u0442\u0432\u043e");
            table.addCell("\u0426\u0435\u043d\u0430 \u0437\u0430 \u0435\u0434\u0438\u043d\u0438\u0446\u0443");
            table.addCell("\u0421\u0443\u043c\u043c\u0430");

            double total = 0;

            for (Product product : products) {
                int qty = cart.getOrDefault(product.getProductId(), 1);
                double price = product.getPrice() != null ? product.getPrice().doubleValue() : 0.0;
                double sum = price * qty;
                total += sum;

                table.addCell(product.getName());
                table.addCell(String.valueOf(qty));
                table.addCell(String.format("%.2f", price));
                table.addCell(String.format("%.2f", sum));
            }

            document.add(table);
            document.add(new Paragraph(" "));
            document.add(new Paragraph("\u0418\u0442\u043e\u0433\u043e: " + String.format("%.2f", total) + " \u0442\u0433"));

            document.close();
        } catch (DocumentException e) {
            throw new IOException("Error while generating PDF", e);
        }
    }

    @GetMapping("/admin/add-product")
    public String showAddProductForm(Model model, HttpSession session) {
        model.addAttribute("brands", brandRepo.findAll());
        model.addAttribute("groups", categoryRepo.findByParentCategoryIdIsNull());
        model.addAttribute("subGroups", List.of());
        model.addAttribute("productForm", new ProductForm());

        Map<String, Object> lastFilters = (Map<String, Object>) session.getAttribute("lastFilters");
        model.addAttribute("filterParams", lastFilters != null ? lastFilters : new HashMap<>());

        return "add_product";
    }

    @PostMapping("/admin/add-product")
    public String addProduct(ProductForm productForm, Model model) {
        // Проверяем, существует ли товар с таким же артикулом
        if (productRepo.existsByArticleCode(productForm.getArticleCode())) {
            model.addAttribute("brands", brandRepo.findAll());
            model.addAttribute("groups", categoryRepo.findByParentCategoryIdIsNull());
            model.addAttribute("subGroups", List.of());
            model.addAttribute("productForm", productForm);
            model.addAttribute("error", "Товар с таким артикулом уже существует!");
            return "add_product"; // вернёмся обратно на страницу с ошибкой
        }

        // Сохраняем товар
        Product product = new Product();
        product.setName(productForm.getName());
        product.setPrice(productForm.getPrice() != null ? productForm.getPrice() : 0.0);
        product.setBrand(brandRepo.findById(productForm.getBrandId()).orElse(null));
        product.setArticleCode(productForm.getArticleCode());
        product = productRepo.save(product);

        // Параметры
        ProductParameters params = new ProductParameters();
        params.setProduct(product);
        params.setParam1(productForm.getParam1());
        params.setParam2(productForm.getParam2());
        params.setParam3(productForm.getParam3());
        params.setParam4(productForm.getParam4());
        params.setParam5(productForm.getParam5());
        parameterRepo.save(params);

        // Категории
        if (productForm.getGroupId() != null) {
            ProductCategories pc = new ProductCategories();
            pc.setProductId(product.getProductId());
            pc.setCategoryId(productForm.getGroupId());
            productCategoriesRepo.save(pc);
        }
        if (productForm.getSubGroupId() != null) {
            ProductCategories pc = new ProductCategories();
            pc.setProductId(product.getProductId());
            pc.setCategoryId(productForm.getSubGroupId());
            productCategoriesRepo.save(pc);
        }

        return "redirect:/";
    }

    @PostMapping("/cart/update")
    @ResponseBody
    public void updateCart(@RequestParam("productId") Integer productId,
                           @RequestParam("quantity") Integer quantity,
                           HttpSession session) {
        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("cart");
        if (cart == null) cart = new HashMap<>();
        if (quantity <= 0) {
            cart.remove(productId);
        } else {
            cart.put(productId, quantity);
        }
        session.setAttribute("cart", cart);
    }


    @GetMapping("/proposal/excel")
    public void downloadProposalExcel(HttpServletResponse response, HttpSession session) throws IOException {
        // Сначала получаем данные из сессии
        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("proposalCart");
        List<Product> products = (List<Product>) session.getAttribute("proposalProducts");
        Double totalSum = (Double) session.getAttribute("proposalTotal");

        // Проверка на null
        if (cart == null || products == null || cart.isEmpty() || totalSum == null) {
            response.sendRedirect("/proposal");
            return;
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=smeta.xlsx");

        try (Workbook workbook = new XSSFWorkbook(); OutputStream out = response.getOutputStream()) {
            Sheet sheet = workbook.createSheet("Смета");

            // Заголовки
            Row header = sheet.createRow(0);
            String[] headers = {"№", "Наименование затрат", "Ед. изм.", "Количество", "Цена, тг", "Сумма, тг"};
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            // Содержимое
            int rowIdx = 1;
            for (int i = 0; i < products.size(); i++) {
                Product product = products.get(i);
                int qty = cart.getOrDefault(product.getProductId(), 1);
                double price = product.getPrice() != null ? product.getPrice() : 0.0;
                double sum = qty * price;

                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(product.getName());
                row.createCell(2).setCellValue("шт.");
                row.createCell(3).setCellValue(qty);
                row.createCell(4).setCellValue(price);
                row.createCell(5).setCellValue(sum);
            }

            // Итоговая строка
            Row totalRow = sheet.createRow(rowIdx);
            totalRow.createCell(4).setCellValue("Итого:");
            totalRow.createCell(5).setCellValue(totalSum);

            workbook.write(out);
        }
    }

}