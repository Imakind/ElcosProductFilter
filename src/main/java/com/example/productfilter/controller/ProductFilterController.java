package com.example.productfilter.controller;

import com.example.productfilter.dto.ProposalHistoryView;
import com.example.productfilter.model.*;
import com.example.productfilter.repository.*;
import com.example.productfilter.service.ExcelImportWithSmartParserService;
import com.example.productfilter.service.ProposalService;
import com.example.productfilter.util.FileNames;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

@Controller
public class ProductFilterController {
    @Autowired private BrandRepository brandRepo;
    @Autowired private CategoryRepository categoryRepo;
    @Autowired private ProductRepository productRepo;
    @Autowired private ProductParameterRepository parameterRepo;
    @Autowired private ProductCategoriesRepository productCategoriesRepo;
    @Autowired private ProposalRepository proposalRepo;
    @Autowired private ProposalSectionRepository proposalSectionRepo;

    private final ProposalService proposalService;
    private final ExcelImportWithSmartParserService excelImportWithSmartParserService;

    private static final String PRODUCT_SECTION_QTY = "productSectionQty"; // Map<Integer, Map<Long,Integer>>
    private static final Logger logger = LoggerFactory.getLogger(ProductFilterController.class);

    public ProductFilterController(ProposalService proposalService,
                                   ExcelImportWithSmartParserService excelImportWithSmartParserService) {
        this.proposalService = proposalService;
        this.excelImportWithSmartParserService = excelImportWithSmartParserService;
    }

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

        List<Proposal> proposalHistory = proposalRepo.findAllByOrderByTimestampDesc();
        model.addAttribute("proposalHistory", proposalHistory);
        return "filter";
    }

    @GetMapping("/filter/options")
    @ResponseBody
    public Map<String, Object> getOptionsByBrand(@RequestParam("brandId") Integer brandId) {
        Map<String, Object> response = new HashMap<>();
        List<Product> products = productRepo.findByBrand_BrandId(brandId);

        Set<Integer> productIds = products.stream().map(Product::getProductId).collect(Collectors.toSet());
        List<Category> allCategories = categoryRepo.findByProducts(productIds);
        List<Category> parentCategories = allCategories.stream()
                .filter(c -> c.getParentCategoryId() == null)
                .collect(Collectors.toList());

        List<ProductParameters> params = parameterRepo.findByProduct_ProductIdIn(productIds);
        Set<String> param1Set = params.stream().map(ProductParameters::getParam1).filter(Objects::nonNull).collect(Collectors.toSet());

        response.put("groups", parentCategories);
        response.put("param1List", param1Set);
        return response;
    }

    @GetMapping("/filter/groups")
    @ResponseBody
    public List<Category> getGroupsByBrand(@RequestParam(value = "brandId", required = false) Integer brandId) {
        List<Product> products = productRepo.findByBrand_BrandId(brandId);
        Set<Integer> productIds = products.stream().map(Product::getProductId).collect(Collectors.toSet());
        return categoryRepo.findParentCategoriesByProducts(productIds);
    }

    @GetMapping("/filter/subgroups")
    @ResponseBody
    public List<Category> getSubGroups(@RequestParam(value = "groupId", required = false) Integer groupId) {
        return categoryRepo.findByParentCategoryId(groupId);
    }

    @GetMapping("/filter/parameters")
    @ResponseBody
    public Map<String, Set<String>> getParameters(
            @RequestParam(value = "brandId", required = false) Integer brandId,
            @RequestParam(value = "groupId", required = false) Integer groupId,
            @RequestParam(value = "subGroupId", required = false) Integer subGroupId
    ) {
        Set<Integer> productIds = productRepo.findAll().stream()
                .filter(p -> brandId == null || p.getBrand().getBrandId().equals(brandId))
                .map(Product::getProductId)
                .collect(Collectors.toSet());

        if (groupId != null || subGroupId != null) {
            List<ProductCategories> allRelations = productCategoriesRepo.findAll();
            if (groupId != null) {
                Set<Integer> groupProducts = allRelations.stream()
                        .filter(pc -> groupId.equals(pc.getCategoryId()))
                        .map(ProductCategories::getProductId)
                        .collect(Collectors.toSet());
                productIds.retainAll(groupProducts);
            }
            if (subGroupId != null) {
                Set<Integer> subGroupProducts = allRelations.stream()
                        .filter(pc -> subGroupId.equals(pc.getCategoryId()))
                        .map(ProductCategories::getProductId)
                        .collect(Collectors.toSet());
                productIds.retainAll(subGroupProducts);
            }
        }

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
        String sessionId = session.getId();
        int pageSize = 21;
        Pageable pageable = PageRequest.of(page, pageSize);

        List<Product> products = productRepo.findAll();
        Set<Integer> productIds = products.stream().map(Product::getProductId).collect(Collectors.toSet());

        if (brandId != null) {
            products = products.stream()
                    .filter(p -> p.getBrand().getBrandId().equals(brandId))
                    .collect(Collectors.toList());
            productIds = products.stream().map(Product::getProductId).collect(Collectors.toSet());
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            products = products.stream()
                    .filter(p -> p.getName() != null && p.getName().toLowerCase().contains(lowerKeyword))
                    .collect(Collectors.toList());
        }

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

        List<ProductParameters> params = parameterRepo.findByProduct_ProductIdIn(productIds);
        if (param1 != null && !param1.isEmpty()) params = params.stream().filter(p -> param1.equals(p.getParam1())).collect(Collectors.toList());
        if (param2 != null && !param2.isEmpty()) params = params.stream().filter(p -> param2.equals(p.getParam2())).collect(Collectors.toList());
        if (param3 != null && !param3.isEmpty()) params = params.stream().filter(p -> param3.equals(p.getParam3())).collect(Collectors.toList());
        if (param4 != null && !param4.isEmpty()) params = params.stream().filter(p -> param4.equals(p.getParam4())).collect(Collectors.toList());
        if (param5 != null && !param5.isEmpty()) params = params.stream().filter(p -> param5.equals(p.getParam5())).collect(Collectors.toList());

        Set<Integer> finalProductIds = !params.isEmpty()
                ? params.stream().map(p -> p.getProduct().getProductId()).collect(Collectors.toSet())
                : productIds;

        products = products.stream().filter(p -> finalProductIds.contains(p.getProductId())).collect(Collectors.toList());

        if (sort != null && !sort.isEmpty()) {
            String[] sorts = sort.split(",");
            for (String s : sorts) {
                switch (s) {
                    case "priceAsc" -> products.sort(Comparator.comparing(Product::getPrice, Comparator.nullsLast(Double::compareTo)));
                    case "priceDesc" -> products.sort(Comparator.comparing(Product::getPrice, Comparator.nullsLast(Double::compareTo)).reversed());
                    case "nameAsc" -> products.sort(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER));
                    case "nameDesc" -> products.sort(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER).reversed());
                    case "brandAsc" -> products.sort(Comparator.comparing(p -> p.getBrand().getBrandName(), String.CASE_INSENSITIVE_ORDER));
                    case "brandDesc" -> products.sort(Comparator.comparing((Product p) -> p.getBrand().getBrandName(), String.CASE_INSENSITIVE_ORDER).reversed());
                }
            }
        }

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), products.size());
        List<Product> pageContent = start < end ? products.subList(start, end) : List.of();
        Page<Product> productPage = new org.springframework.data.domain.PageImpl<>(pageContent, pageable, products.size());

        model.addAttribute("products", productPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", productPage.getTotalPages());

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

        if ("XMLHttpRequest".equals(request.getHeader("X-Requested-With"))) {
            return "fragments/product_list :: productList";
        }

        List<ProposalHistoryView> historyList = proposalService.getAllProposals();
        model.addAttribute("proposalHistory", historyList);
        return "filter";
    }

    @PostMapping("/cart/add")
    @ResponseBody
    public void addToCart(@RequestParam("productId") Integer productId,
                          @RequestParam(value = "sectionId", required = false) Long sectionId,
                          HttpSession session) {
        @SuppressWarnings("unchecked")
        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("cart");
        if (cart == null) cart = new HashMap<>();
        cart.put(productId, cart.getOrDefault(productId, 0) + 1);
        session.setAttribute("cart", cart);

        Map<Long, String> sec = ensureSections(session);
        Long sid = (sectionId != null && sec.containsKey(sectionId)) ? sectionId : 1L;
        ensureProductSection(session).put(productId, sid);
    }

    // === НОВОЕ: переименование секции («папки») ===
    @PostMapping("/cart/sections/rename")
    @ResponseBody
    public Map<String, Object> renameSection(@RequestParam Long sectionId,
                                             @RequestParam String name,
                                             HttpSession s) {
        if (name == null || name.isBlank()) {
            return Map.of("ok", false, "msg", "Пустое имя");
        }
        Map<Long, String> sec = ensureSections(s);
        if (!sec.containsKey(sectionId)) {
            return Map.of("ok", false, "msg", "Секции нет");
        }
        sec.put(sectionId, name.trim());
        s.setAttribute("sections", sec);
        return Map.of("ok", true, "id", sectionId, "name", name.trim());
    }

    // === НОВОЕ: установка количества товара и пересчёт сумм ===
    @PostMapping("/cart/set-qty")
    @ResponseBody
    public Map<String, Object> setQuantity(@RequestParam Integer productId,
                                           @RequestParam Integer qty,
                                           HttpSession s) {
        if (productId == null) return Map.of("ok", false, "msg", "Нет productId");
        if (qty == null || qty < 1) qty = 1;

        @SuppressWarnings("unchecked")
        Map<Integer,Integer> cart = (Map<Integer,Integer>) s.getAttribute("cart");
        if (cart == null || !cart.containsKey(productId)) {
            return Map.of("ok", false, "msg", "Товар не в корзине");
        }

        cart.put(productId, qty);
        s.setAttribute("cart", cart);

        // ужать разбиения по секциям, если они больше нового qty
        Map<Integer, Map<Long,Integer>> splits = ensureProductSectionQty(s);
        Map<Long,Integer> bySection = splits.get(productId);
        if (bySection != null && !bySection.isEmpty()) {
            int assigned = bySection.values().stream().mapToInt(v -> v == null ? 0 : v).sum();
            if (assigned > qty) {
                int rest = qty;
                Long dominant = bySection.entrySet().stream()
                        .max(Comparator.comparingInt(e -> e.getValue() == null ? 0 : e.getValue()))
                        .map(Map.Entry::getKey).orElse(null);
                Map<Long,Integer> resized = new LinkedHashMap<>();
                for (var e : bySection.entrySet()) {
                    int v = e.getValue() == null ? 0 : e.getValue();
                    int nv = (int) Math.floor((v * 1.0 * qty) / Math.max(1, assigned));
                    resized.put(e.getKey(), nv);
                    rest -= nv;
                }
                if (dominant != null && rest > 0) {
                    resized.merge(dominant, rest, Integer::sum);
                }
                resized.entrySet().removeIf(x -> x.getValue() == null || x.getValue() <= 0);
                if (resized.isEmpty()) splits.remove(productId); else splits.put(productId, resized);
                s.setAttribute(PRODUCT_SECTION_QTY, splits);
            }
        }

        // расчёт цен
        Map<Integer, Double> coeff = (Map<Integer, Double>) s.getAttribute("coefficientMap");
        if (coeff == null) coeff = new HashMap<>();

        List<Product> products = cart.isEmpty()
                ? List.of()
                : productRepo.findAllById(cart.keySet());

        double totalSum = 0.0;
        int totalQty = 0;
        Map<Integer, Double> lineSums = new HashMap<>();
        Map<Integer, Double> unitPrices = new HashMap<>();

        for (Product p : products) {
            int q = cart.getOrDefault(p.getProductId(), 0);
            double base = p.getPrice() == null ? 0.0 : p.getPrice();
            double k = coeff.getOrDefault(p.getProductId(), 1.0);
            double unit = base * k;
            double line = unit * q;
            unitPrices.put(p.getProductId(), unit);
            lineSums.put(p.getProductId(), line);
            totalSum += line;
            totalQty += q;
        }

        return Map.of(
                "ok", true,
                "productId", productId,
                "qty", qty,
                "unitPrice", unitPrices.getOrDefault(productId, 0.0),
                "lineSum", lineSums.getOrDefault(productId, 0.0),
                "totalQty", totalQty,
                "totalSum", totalSum
        );
    }

    @GetMapping("/cart")
    public String viewCart(Model model, HttpSession session, HttpServletResponse resp) {
        // no-cache, чтобы избежать визуального «отката» из-за кэша браузера
        resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        resp.setHeader("Pragma", "no-cache");

        String sessionId = session.getId();

        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("cart");
        Map<Integer, Double> coefficientMap = (Map<Integer, Double>) session.getAttribute("coefficientMap");
        if (cart == null) cart = new HashMap<>();
        if (coefficientMap == null) coefficientMap = new HashMap<>();

        List<Product> products = (!cart.isEmpty()) ? productRepo.findAllById(cart.keySet()) : List.of();

        Map<Integer, Double> unitPriceMap = new HashMap<>();
        for (Product p : products) {
            unitPriceMap.put(p.getProductId(), p.getPrice() != null ? p.getPrice() : 0.0);
        }
        model.addAttribute("unitPriceMap", unitPriceMap);

        List<ProductParameters> parameters = parameterRepo.findByProduct_ProductIdIn(
                products.stream().map(Product::getProductId).collect(Collectors.toSet())
        );

        model.addAttribute("cartProducts", products);
        model.addAttribute("cartParams", parameters);
        model.addAttribute("quantities", cart);
        model.addAttribute("coefficientMap", coefficientMap);

        Set<Integer> productIds = products.stream().map(Product::getProductId).collect(Collectors.toSet());
        List<ProductCategories> links = productCategoriesRepo.findByProductIdIn(productIds);
        Set<Integer> categoryIds = links.stream().map(ProductCategories::getCategoryId).collect(Collectors.toSet());
        List<Category> allCategories = categoryRepo.findAllById(categoryIds);
        Map<Integer, Category> categoryMap = allCategories.stream().collect(Collectors.toMap(Category::getCategoryId, c -> c));

        Map<Integer, List<Category>> productIdToCategories = new HashMap<>();
        for (ProductCategories link : links) {
            Integer productId = link.getProductId();
            Integer categoryId = link.getCategoryId();
            Category category = categoryMap.get(categoryId);
            if (category != null) {
                productIdToCategories.computeIfAbsent(productId, k -> new ArrayList<>()).add(category);
            }
        }
        model.addAttribute("productCategoriesMap", productIdToCategories);

        Map<String, Object> lastFilters = (Map<String, Object>) session.getAttribute("lastFilters");
        model.addAttribute("filterParams", lastFilters != null ? lastFilters : new HashMap<>());

        double totalSum = 0.0;
        for (Product product : products) {
            int qty = cart.getOrDefault(product.getProductId(), 1);
            double price = product.getPrice() != null ? product.getPrice() : 0.0;
            double coeff = coefficientMap.getOrDefault(product.getProductId(), 1.0);
            totalSum += qty * price * coeff;
        }
        model.addAttribute("totalSum", totalSum);

        int totalQuantity = cart.values().stream().mapToInt(Integer::intValue).sum();
        model.addAttribute("totalQuantity", totalQuantity);

        List<ProposalHistoryView> history = proposalRepo.findEstimateHistoryBySessionId(sessionId);
        model.addAttribute("proposalHistory", history);

        return "cart";
    }

    @PostMapping("/cart/coefficient")
    @ResponseBody
    public void updateCoefficient(@RequestParam("productId") Integer productId,
                                  @RequestParam("coefficient") Double coefficient,
                                  HttpSession session) {
        Map<Integer, Double> coefficientMap = (Map<Integer, Double>) session.getAttribute("coefficientMap");
        if (coefficientMap == null) coefficientMap = new HashMap<>();
        coefficientMap.put(productId, coefficient);
        session.setAttribute("coefficientMap", coefficientMap);
    }

    @PostMapping("/cart/remove")
    @ResponseBody
    public void removeFromCart(@RequestParam("productId") Integer productId, HttpSession session) {
        @SuppressWarnings("unchecked")
        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("cart");
        if (cart == null) return;

        int qty = cart.getOrDefault(productId, 0);
        if (qty > 1) {
            cart.put(productId, qty - 1);
        } else {
            cart.remove(productId);
            ensureProductSection(session).remove(productId);
            Map<Integer, Map<Long,Integer>> splits = ensureProductSectionQty(session);
            splits.remove(productId);
        }
        session.setAttribute("cart", cart);
    }

    @PostMapping("/cart/confirm")
    public String confirmCart(HttpSession session, Model model) {
        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("cart");
        if (cart == null || cart.isEmpty()) return "redirect:/cart";

        List<Product> products = productRepo.findAllById(cart.keySet());
        Map<Integer, Double> coefficientMap = (Map<Integer, Double>) session.getAttribute("coefficientMap");
        if (coefficientMap == null) coefficientMap = new HashMap<>();

        Map<Integer, Double> finalPrices = new HashMap<>();
        Map<Integer, Double> totalSums = new HashMap<>();
        double totalSum = 0.0;

        for (Product product : products) {
            int qty = cart.getOrDefault(product.getProductId(), 1);
            double basePrice = product.getPrice() != null ? product.getPrice() : 0.0;
            double coeff = coefficientMap.getOrDefault(product.getProductId(), 1.0);
            double finalPrice = basePrice * coeff;
            double total = finalPrice * qty;

            finalPrices.put(product.getProductId(), finalPrice);
            totalSums.put(product.getProductId(), total);
            totalSum += total;
        }

        List<ProductParameters> parameters = parameterRepo.findByProduct_ProductIdIn(cart.keySet());
        Map<Integer, ProductParameters> paramMap = parameters.stream()
                .collect(Collectors.toMap(p -> p.getProduct().getProductId(), Function.identity()));

        model.addAttribute("products", products);
        model.addAttribute("quantities", cart);
        model.addAttribute("coefficientMap", coefficientMap);
        model.addAttribute("finalPrices", finalPrices);
        model.addAttribute("totalSums", totalSums);
        model.addAttribute("paramMap", paramMap);
        model.addAttribute("totalSum", totalSum);
        model.addAttribute("totalQuantity", cart.values().stream().mapToInt(i -> i).sum());

        session.setAttribute("proposalCart", new HashMap<>(cart));
        session.setAttribute("proposalCoefficients", new HashMap<>(coefficientMap));
        session.setAttribute("proposalProducts", products);
        session.setAttribute("proposalTotal", totalSum);

        Map<String, Object> lastFilters = (Map<String, Object>) session.getAttribute("lastFilters");
        model.addAttribute("filterParams", lastFilters != null ? lastFilters : new HashMap<>());

        return "proposal";
    }

    // ===== УДАЛЁН эндпойнт /proposal/pdf и все PDF-зависимости =====

    @GetMapping("/proposal/excel")
    public void downloadProposalExcel(HttpServletResponse response, HttpSession session) throws IOException {
        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("proposalCart");
        List<Product> products = (List<Product>) session.getAttribute("proposalProducts");
        Double totalSum = (Double) session.getAttribute("proposalTotal");
        Map<Integer, Double> coefficientMap = (Map<Integer, Double>) session.getAttribute("proposalCoefficients");
        if (coefficientMap == null) coefficientMap = new HashMap<>();

        if (cart == null || products == null || cart.isEmpty() || totalSum == null) {
            response.sendRedirect("/proposal");
            return;
        }

        String projectName = Optional.ofNullable((String) session.getAttribute("projectName")).orElse("Проект");
        String user = FileNames.currentUser();
        String fnameXlsx = FileNames.smeta(projectName, user);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", rfc5987ContentDisposition(fnameXlsx));

        try (Workbook workbook = new XSSFWorkbook(); OutputStream out = response.getOutputStream()) {
            Sheet sheet = workbook.createSheet("Смета");

            DataFormat format = workbook.createDataFormat();
            CellStyle priceStyle = workbook.createCellStyle();
            priceStyle.setDataFormat(format.getFormat("# ##0.00"));

            Row header = sheet.createRow(0);
            String[] headers = {"№", "Наименование затрат", "Ед. изм.", "Количество", "Цена, тг", "Сумма, тг"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);

            int rowIdx = 1;
            double total = 0.0;

            for (int i = 0; i < products.size(); i++) {
                Product product = products.get(i);
                int qty = cart.getOrDefault(product.getProductId(), 1);
                double basePrice = product.getPrice() != null ? product.getPrice() : 0.0;
                double coeff = coefficientMap.getOrDefault(product.getProductId(), 1.0);
                double finalPrice = basePrice * coeff;
                double sum = qty * finalPrice;
                total += sum;

                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(i + 1);
                row.createCell(1).setCellValue(product.getName());
                row.createCell(2).setCellValue("шт.");
                row.createCell(3).setCellValue(qty);

                Cell finalPriceCell = row.createCell(4);
                finalPriceCell.setCellValue(finalPrice);
                finalPriceCell.setCellStyle(priceStyle);

                Cell sumCell = row.createCell(5);
                sumCell.setCellValue(sum);
                sumCell.setCellStyle(priceStyle);
            }

            Row totalRow = sheet.createRow(rowIdx);
            totalRow.createCell(4).setCellValue("Итого:");
            Cell totalCell = totalRow.createCell(5);
            totalCell.setCellValue(total);
            totalCell.setCellStyle(priceStyle);

            workbook.write(out);
        }
    }

    @GetMapping("/proposal/excel-kp")
    public void downloadProposalExcelKp(HttpServletResponse response, HttpSession session) throws IOException {
        String projectName = Optional.ofNullable((String) session.getAttribute("projectName")).orElse("Проект");
        String user = FileNames.currentUser();
        String fnameXlsx = FileNames.kpXlsx(projectName, user);

        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("proposalCart");
        List<Product> products = (List<Product>) session.getAttribute("proposalProducts");
        Map<Integer, Double> coefficientMap = (Map<Integer, Double>) session.getAttribute("proposalCoefficients");
        if (cart == null || products == null || cart.isEmpty()) { response.sendRedirect("/cart"); return; }
        if (coefficientMap == null) coefficientMap = new HashMap<>();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Коммерческое предложение");
            sheet.setDefaultColumnWidth(20);

            DataFormat df = workbook.createDataFormat();
            CellStyle num1 = workbook.createCellStyle(); num1.setDataFormat(df.getFormat("# ##0.0"));
            CellStyle int0 = workbook.createCellStyle(); int0.setDataFormat(df.getFormat("# ##0"));

            Row header = sheet.createRow(0);
            String[] columns = {"№","Наименование","Артикул","Бренд","Кол-во","Цена","Сумма"};
            for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);

            int rowIdx = 1, index = 1, totalQty = 0;
            double totalSum = 0.0;

            for (Product p : products) {
                int qty = cart.getOrDefault(p.getProductId(), 1);
                double basePrice = p.getPrice() != null ? p.getPrice() : 0.0;
                double coeff = coefficientMap.getOrDefault(p.getProductId(), 1.0);
                double price = basePrice * coeff;
                double sum = price * qty;

                Row r = sheet.createRow(rowIdx++);
                r.createCell(0).setCellValue(index++);
                r.createCell(1).setCellValue(p.getName());
                r.createCell(2).setCellValue(p.getArticleCode());
                r.createCell(3).setCellValue(p.getBrand().getBrandName());

                Cell c;
                c = r.createCell(4); c.setCellValue(qty);   c.setCellStyle(int0);
                c = r.createCell(5); c.setCellValue(price); c.setCellStyle(num1);
                c = r.createCell(6); c.setCellValue(sum);   c.setCellStyle(num1);

                totalQty += qty;
                totalSum += sum;
            }

            Row totalRow = sheet.createRow(rowIdx);
            totalRow.createCell(0).setCellValue("Итого:");
            sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, 3));
            Cell cq = totalRow.createCell(4); cq.setCellValue(totalQty); cq.setCellStyle(int0);
            Cell cs = totalRow.createCell(6); cs.setCellValue(totalSum); cs.setCellStyle(num1);

            workbook.write(baos);
        }

        byte[] xlsx = baos.toByteArray();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", rfc5987ContentDisposition(fnameXlsx));
        response.setContentLength(xlsx.length);

        try (OutputStream out = response.getOutputStream()) {
            out.write(xlsx);
        }
    }

    @GetMapping("/proposal/download/{filename}")
    @ResponseBody
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) throws IOException {
        Path file = Paths.get("generated", filename);
        if (!Files.exists(file)) return ResponseEntity.notFound().build();

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    public static String formatPrice(Double price) {
        if (price == null) return "0";
        return String.format("%,.1f", price)
                .replace(',', ' ')
                .replace('.', ',');
    }

    @GetMapping("/cart/excel")
    public void downloadCartExcel(HttpServletResponse response, HttpSession session) throws IOException {
        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("cart");
        Map<Integer, Double> coefficientMap = (Map<Integer, Double>) session.getAttribute("coefficientMap");
        if (cart == null || cart.isEmpty()) { response.sendRedirect("/cart"); return; }
        if (coefficientMap == null) coefficientMap = new HashMap<>();

        List<Product> products = productRepo.findAllById(cart.keySet());

        String projectName = Optional.ofNullable((String) session.getAttribute("projectName")).orElse("Проект");
        String user = FileNames.currentUser();
        String fname = FileNames.smeta(projectName, user);

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", rfc5987ContentDisposition(fname));

        try (Workbook workbook = new XSSFWorkbook(); OutputStream out = response.getOutputStream()) {
            Sheet sheet = workbook.createSheet("Смета");
            DataFormat format = workbook.createDataFormat();
            CellStyle style = workbook.createCellStyle();
            style.setDataFormat(format.getFormat("# ##0.0"));

            Row header = sheet.createRow(0);
            String[] cols = {"№", "Наименование", "Артикул", "Бренд", "Кол-во",
                    "Базовая цена", "Сумма базовая", "Коэф.", "Цена", "Сумма", "Маржа"};
            for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);

            int rowIdx = 1;
            int index = 1;
            double totalBase = 0.0;
            double totalFinal = 0.0;
            int totalQty = 0;

            for (Product product : products) {
                int qty = cart.getOrDefault(product.getProductId(), 1);
                double basePrice = product.getPrice() != null ? product.getPrice() : 0.0;
                double coeff = coefficientMap.getOrDefault(product.getProductId(), 1.0);
                double baseSum = basePrice * qty;
                double unitPrice = basePrice * coeff;
                double finalSum = unitPrice * qty;
                double margin = finalSum - baseSum;

                Row row = sheet.createRow(rowIdx++);
                int col = 0;
                row.createCell(col++).setCellValue(index++);
                row.createCell(col++).setCellValue(product.getName());
                row.createCell(col++).setCellValue(product.getArticleCode());
                row.createCell(col++).setCellValue(product.getBrand().getBrandName());
                row.createCell(col++).setCellValue(qty);

                Cell basePriceCell = row.createCell(col++); basePriceCell.setCellValue(basePrice); basePriceCell.setCellStyle(style);
                Cell baseSumCell   = row.createCell(col++); baseSumCell.setCellValue(baseSum);     baseSumCell.setCellStyle(style);
                Cell coeffCell     = row.createCell(col++); coeffCell.setCellValue(coeff);         coeffCell.setCellStyle(style);
                Cell unitPriceCell = row.createCell(col++); unitPriceCell.setCellValue(unitPrice); unitPriceCell.setCellStyle(style);
                Cell finalSumCell  = row.createCell(col++); finalSumCell.setCellValue(finalSum);   finalSumCell.setCellStyle(style);
                Cell marginCell    = row.createCell(col++); marginCell.setCellValue(margin);       marginCell.setCellStyle(style);

                totalBase += baseSum;
                totalFinal += finalSum;
                totalQty += qty;
            }

            Row totalRow = sheet.createRow(rowIdx);
            totalRow.createCell(3).setCellValue("Итого:");
            totalRow.createCell(4).setCellValue(totalQty);
            totalRow.createCell(6).setCellValue(totalBase);
            totalRow.createCell(9).setCellValue(totalFinal);
            totalRow.createCell(10).setCellValue(totalFinal - totalBase);

            workbook.write(out);
        }
    }

    @PostMapping("/cart/save-estimate")
    @Transactional
    public String saveEstimate(@RequestParam String projectName,
                               @RequestParam(value = "folderId", required = false) Long folderId,
                               @RequestParam(value = "folderMappingJson", required = false) String folderMappingJson,
                               HttpSession session) {
        String user = FileNames.currentUser();
        String fileName = FileNames.smeta(projectName, user);

        @SuppressWarnings("unchecked")
        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("cart");
        @SuppressWarnings("unchecked")
        Map<Integer, Double> coefficientMap = (Map<Integer, Double>) session.getAttribute("coefficientMap");
        if (cart == null || cart.isEmpty()) return "redirect:/cart?error=empty";
        if (coefficientMap == null) coefficientMap = new HashMap<>();

        List<Product> products = productRepo.findAllById(cart.keySet());

        Proposal proposal = new Proposal();
        proposal.setName(projectName);
        proposal.setFileType("estimate");
        proposal.setTimestamp(LocalDateTime.now());
        proposal.setSessionId(session.getId());
        proposal.setCreatedBy(user);
        proposalRepo.save(proposal);

        @SuppressWarnings("unchecked")
        Map<Long, String> sec = (Map<Long, String>) session.getAttribute("sections");
        @SuppressWarnings("unchecked")
        Map<Long, Long> parents = (Map<Long, Long>) session.getAttribute("sectionParent");
        if (sec == null) sec = new LinkedHashMap<>();
        if (parents == null) parents = new HashMap<>();
        sec.putIfAbsent(1L, "Общий");
        parents.putIfAbsent(1L, null);

        Map<Long, ProposalSection> savedBySessionId = new LinkedHashMap<>();
        for (Map.Entry<Long, String> e : sec.entrySet()) {
            ProposalSection ps = new ProposalSection();
            ps.setProposal(proposal);
            ps.setName(e.getValue());
            savedBySessionId.put(e.getKey(), ps);
        }
        for (Map.Entry<Long, ProposalSection> e : savedBySessionId.entrySet()) {
            Long sessId = e.getKey();
            ProposalSection node = e.getValue();
            Long parentSessId = parents.get(sessId);
            node.setParent(parentSessId != null ? savedBySessionId.get(parentSessId) : null);
            proposalSectionRepo.save(node);
        }

        Map<Integer, Long> folderMap = new HashMap<>();
        if (folderMappingJson != null && !folderMappingJson.isBlank()) {
            try {
                var om = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Long> tmp = om.readValue(folderMappingJson,
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, Long>>() {});
                for (Map.Entry<String, Long> e : tmp.entrySet()) {
                    folderMap.put(Integer.valueOf(e.getKey()), e.getValue());
                }
            } catch (Exception ignore) {}
        }

        Map<Integer, Map<Long, Integer>> splits = ensureProductSectionQty(session);

        double totalSum = 0.0;
        for (Product product : products) {
            int pid = product.getProductId();
            int qtyTotal = cart.getOrDefault(pid, 0);
            if (qtyTotal <= 0) continue;

            double basePrice = product.getPrice() != null ? product.getPrice() : 0.0;
            double k = coefficientMap.getOrDefault(pid, 1.0);

            Map<Long, Integer> bySection = splits.get(pid);
            if (bySection != null && !bySection.isEmpty()) {
                int assigned = 0;
                for (Map.Entry<Long, Integer> e : bySection.entrySet()) {
                    int q = e.getValue() != null ? e.getValue() : 0;
                    if (q <= 0) continue;

                    ProposalItem item = new ProposalItem();
                    item.setProduct(product);
                    item.setQuantity(q);
                    item.setBasePrice(basePrice);
                    item.setCoefficient(k);
                    item.setSectionNode(savedBySessionId.getOrDefault(e.getKey(), savedBySessionId.get(1L)));
                    proposal.addItem(item);

                    totalSum += q * basePrice * k;
                    assigned += q;
                }
                int rest = Math.max(0, qtyTotal - assigned);
                if (rest > 0) {
                    Long sessSectionId = folderMap.getOrDefault(pid, 1L);
                    ProposalItem item = new ProposalItem();
                    item.setProduct(product);
                    item.setQuantity(rest);
                    item.setBasePrice(basePrice);
                    item.setCoefficient(k);
                    item.setSectionNode(savedBySessionId.getOrDefault(sessSectionId, savedBySessionId.get(1L)));
                    proposal.addItem(item);
                    totalSum += rest * basePrice * k;
                }
            } else {
                Long sessSectionId = folderMap.getOrDefault(pid, 1L);
                ProposalItem item = new ProposalItem();
                item.setProduct(product);
                item.setQuantity(qtyTotal);
                item.setBasePrice(basePrice);
                item.setCoefficient(k);
                item.setSectionNode(savedBySessionId.getOrDefault(sessSectionId, savedBySessionId.get(1L)));
                proposal.addItem(item);
                totalSum += qtyTotal * basePrice * k;
            }
        }

        proposal.setTotalSum(totalSum);

        Path dir = Paths.get("files", "estimates");
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(fileName);

            try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
                var sh = wb.createSheet("Смета");
                int r = 0;
                var header = sh.createRow(r++);
                header.createCell(0).setCellValue("Наименование");
                header.createCell(1).setCellValue("Артикул");
                header.createCell(2).setCellValue("Бренд");
                header.createCell(3).setCellValue("Кол-во");
                header.createCell(4).setCellValue("Коэфф.");
                header.createCell(5).setCellValue("Цена");
                header.createCell(6).setCellValue("Сумма");

                for (ProposalItem it : proposal.getItems()) {
                    var p = it.getProduct();
                    var row = sh.createRow(r++);
                    row.createCell(0).setCellValue(p.getName());
                    row.createCell(1).setCellValue(p.getArticleCode() != null ? p.getArticleCode() : "");
                    row.createCell(2).setCellValue(p.getBrand() != null ? p.getBrand().getBrandName() : "");
                    row.createCell(3).setCellValue(it.getQuantity());
                    row.createCell(4).setCellValue(it.getCoefficient() != null ? it.getCoefficient() : 1.0);
                    row.createCell(5).setCellValue(it.getBasePrice() != null ? it.getBasePrice() : 0.0);
                    double sum = (it.getQuantity()) * (it.getCoefficient() != null ? it.getCoefficient() : 1.0)
                            * (it.getBasePrice() != null ? it.getBasePrice() : 0.0);
                    row.createCell(6).setCellValue(sum);
                }
                var totalRow = sh.createRow(r++);
                totalRow.createCell(5).setCellValue("Итого:");
                totalRow.createCell(6).setCellValue(totalSum);

                try (java.io.OutputStream os = Files.newOutputStream(target)) {
                    wb.write(os);
                }
            }

            proposal.setFilePath(target.toString());
        } catch (Exception e) {
            proposal.setFilePath(null);
        }

        proposalRepo.save(proposal);
        session.setAttribute("projectName", projectName);
        return "redirect:/cart?saved=1";
    }

    @ModelAttribute("proposalHistory")
    public List<Proposal> getHistory(HttpSession session) {
        return proposalRepo.findBySessionIdAndFileTypeOrderByTimestampDesc(session.getId(), "estimate");
    }

    @PostMapping("/cart/load-estimate")
    public String loadEstimate(@RequestParam("proposalId") Long proposalId, HttpSession session) {
        proposalService.loadEstimateToSession(proposalId, session);

        proposalRepo.findById(proposalId).ifPresent(p -> {
            List<ProposalSection> nodes = proposalSectionRepo.findByProposalId(proposalId);
            Map<Long, String> sec = ensureSections(session);
            Map<Long, Long> parents = ensureSectionParents(session);
            sec.clear(); parents.clear();
            for (ProposalSection n : nodes) {
                sec.put(n.getId(), n.getName());
                parents.put(n.getId(), n.getParent() != null ? n.getParent().getId() : null);
            }

            Long rootId = parents.entrySet().stream().filter(e -> e.getValue() == null)
                    .map(Map.Entry::getKey).findFirst()
                    .orElseGet(() -> {
                        if (sec.isEmpty()) { sec.put(1L, "Общий"); parents.put(1L, null); return 1L; }
                        return sec.keySet().iterator().next();
                    });

            Map<Integer, Long> ps = ensureProductSection(session);
            Map<Integer, Map<Long,Integer>> splits = ensureProductSectionQty(session);
            ps.clear(); splits.clear();
            if (p.getItems() != null) {
                p.getItems().forEach(it -> {
                    if (it.getProduct() == null) return;
                    int pid = it.getProduct().getProductId();
                    Long nodeId = null;
                    if (it.getSectionNode() != null) nodeId = it.getSectionNode().getId();
                    else if (it.getSectionId() != null && sec.containsKey(it.getSectionId())) nodeId = it.getSectionId();
                    if (nodeId == null) nodeId = rootId;
                    int q = it.getQuantity() != null ? it.getQuantity() : 0;
                    if (q > 0) splits.computeIfAbsent(pid, k -> new HashMap<>()).merge(nodeId, q, Integer::sum);
                });

                session.setAttribute(PRODUCT_SECTION_QTY, splits);

                ps.clear();
                for (Map.Entry<Integer, Map<Long, Integer>> e : splits.entrySet()) {
                    Integer pid = e.getKey();
                    Map<Long, Integer> bySec = e.getValue();
                    if (bySec != null && !bySec.isEmpty()) {
                        Long dominant = bySec.entrySet().stream()
                                .max(Comparator.comparingInt(x -> x.getValue() != null ? x.getValue() : 0))
                                .map(Map.Entry::getKey)
                                .orElse(rootId);
                        ps.put(pid, dominant);
                    } else {
                        ps.put(pid, rootId);
                    }
                }

                if (ps.isEmpty() && p.getItems() != null) {
                    for (ProposalItem it : p.getItems()) {
                        if (it.getProduct() == null) continue;
                        int pid = it.getProduct().getProductId();
                        Long nodeId = null;
                        if (it.getSectionNode() != null) nodeId = it.getSectionNode().getId();
                        else if (it.getSectionId() != null && sec.containsKey(it.getSectionId())) nodeId = it.getSectionId();
                        if (nodeId == null) nodeId = rootId;
                        ps.putIfAbsent(pid, nodeId);
                    }
                }
                session.setAttribute("productSection", ps);
            }

            Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("cart");
            if (cart == null || cart.isEmpty()) {
                Map<Integer, Integer> newCart = new HashMap<>();
                Map<Integer, Double> coeffs = new HashMap<>();
                if (p.getItems() != null) {
                    p.getItems().forEach(it -> {
                        if (it.getProduct() != null) {
                            int pid = it.getProduct().getProductId();
                            newCart.put(pid, it.getQuantity() != null ? it.getQuantity() : 1);
                            coeffs.put(pid, it.getCoefficient() != null ? it.getCoefficient() : 1.0);
                        }
                    });
                }
                session.setAttribute("cart", newCart);
                session.setAttribute("coefficientMap", coeffs);
            }

            long maxId = sec.keySet().stream().mapToLong(Long::longValue).max().orElse(0L);
            session.setAttribute("sectionSeq", maxId + 1);
        });

        return "redirect:/cart";
    }

    @GetMapping("/cart/history")
    public String viewHistory(HttpSession session, Model model) {
        List<Proposal> history = proposalRepo.findAllByOrderByTimestampDesc();
        model.addAttribute("history", history);
        return "cart_history";
    }

    @GetMapping("/clear-cart-and-return")
    public String clearCartAndReturn(HttpSession session) {
        session.removeAttribute("cart");
        session.removeAttribute("coefficientMap");
        return "redirect:/";
    }

    @PostMapping("/cart/upload-estimate")
    public String uploadEstimate(@RequestParam("file") MultipartFile file, HttpSession session, RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Файл пустой");
            return "redirect:/cart";
        }

        Map<Integer, Integer> cart = new HashMap<>();
        Map<Integer, Double> coefficientMap = new HashMap<>();

        try (InputStream is = file.getInputStream()) {
            Workbook workbook = new XSSFWorkbook(is);
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Cell nameCell = row.getCell(1);
                Cell articleCell = row.getCell(2);
                Cell brandCell = row.getCell(3);
                Cell qtyCell = row.getCell(4);
                Cell coeffCell = row.getCell(7);

                if (nameCell == null || articleCell == null || qtyCell == null) continue;

                String articleCode = articleCell.getStringCellValue().trim();
                int quantity = (int) qtyCell.getNumericCellValue();
                double coefficient = coeffCell != null ? coeffCell.getNumericCellValue() : 1.0;

                Optional<Product> optionalProduct = productRepo.findByArticleCode(articleCode);
                if (optionalProduct.isPresent()) {
                    Product product = optionalProduct.get();
                    cart.put(product.getProductId(), quantity);
                    coefficientMap.put(product.getProductId(), coefficient);
                }
            }

            session.setAttribute("cart", cart);
            session.setAttribute("coefficientMap", coefficientMap);
            redirectAttributes.addFlashAttribute("message", "Смета успешно загружена");
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Ошибка при обработке файла: " + e.getMessage());
        }

        return "redirect:/cart";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/import-excel")
    public String importExcel(@RequestParam("file") MultipartFile file, RedirectAttributes redirectAttributes) {
        try {
            excelImportWithSmartParserService.importFromExcel(file);
            redirectAttributes.addFlashAttribute("message", "Файл успешно загружен и обработан.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при загрузке файла: " + e.getMessage());
        }
        return "redirect:/admin/add-product";
    }

    @PostMapping("/cart/remove-selected")
    @ResponseBody
    public void removeSelected(@RequestParam("productIds") List<Integer> productIds, HttpSession session) {
        @SuppressWarnings("unchecked")
        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("cart");
        @SuppressWarnings("unchecked")
        Map<Integer, Double> coefficientMap = (Map<Integer, Double>) session.getAttribute("coefficientMap");

        if (cart == null) return;
        for (Integer id : productIds) {
            cart.remove(id);
            ensureProductSection(session).remove(id);
            Map<Integer, Map<Long,Integer>> splits = ensureProductSectionQty(session);
            splits.remove(id);
            if (coefficientMap != null) coefficientMap.remove(id);
        }
        session.setAttribute("cart", cart);
        session.setAttribute("coefficientMap", coefficientMap);
    }

    @RestController
    @RequestMapping("/filter")
    public class ParameterLabelsController {
        private final ProductRepository productRepo;
        private final ProductParameterRepository parameterRepo;
        private final ProductCategoriesRepository productCategoriesRepo;

        public ParameterLabelsController(ProductRepository productRepo,
                                         ProductParameterRepository parameterRepo,
                                         ProductCategoriesRepository productCategoriesRepo) {
            this.productRepo = productRepo;
            this.parameterRepo = parameterRepo;
            this.productCategoriesRepo = productCategoriesRepo;
        }

        @GetMapping("/parameter-labels")
        public Map<String, String> getParameterLabels(
                @RequestParam(value = "groupId", required = false) Integer groupId,
                @RequestParam(value = "subGroupId", required = false) Integer subGroupId
        ) {
            Map<String, String> labels = new LinkedHashMap<>();
            labels.put("param1", "Параметр 1");
            labels.put("param2", "Параметр 2");
            labels.put("param3", "Параметр 3");
            labels.put("param4", "Параметр 4");
            labels.put("param5", "Параметр 5");

            Integer catId = (subGroupId != null ? subGroupId : groupId);
            if (catId == null) return labels;

            List<ProductCategories> links = productCategoriesRepo.findAll();
            Set<Integer> productIds = links.stream()
                    .filter(pc -> catId.equals(pc.getCategoryId()))
                    .map(ProductCategories::getProductId)
                    .collect(toSet());
            if (productIds.isEmpty()) return labels;

            List<ProductParameters> params = parameterRepo.findByProduct_ProductIdIn(productIds);
            if (params.isEmpty()) return labels;

            Map<Integer, Product> productMap = productRepo.findAllById(productIds).stream()
                    .collect(toMap(Product::getProductId, p -> p));

            Map<String, String> inferred = ParamLabelInferer.infer(params, productMap);
            inferred.forEach((k, v) -> labels.put(k, v == null ? "" : v));
            return labels;
        }

        private static final class ParamLabelInferer {
            private static final Pattern POLES = Pattern.compile("^[1-4]\\s*[pр]$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            private static final Pattern CURRENT_A = Pattern.compile("^\\d{1,3}\\s*[aа]$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            private static final Pattern CHAR_BCD = Pattern.compile("^[bcdBCD]$");
            private static final Pattern KA_CUTOFF = Pattern.compile("^\\d{1,2}(?:[\\.,]\\d{1,2})?\\s*(?:k|к)\\s*a$", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            private static final Pattern THICKNESS = Pattern.compile("^[0-3](?:[\\.,]\\d)$");
            private static final Pattern NUMBER = Pattern.compile("^\\d+(?:[\\.,]\\d+)?$");
            private static final Pattern DIM_KEYWORDS = Pattern.compile("лоток|профиль|труба|переходник|короб", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

            private static final double COVERAGE_THRESHOLD = 0.30;

            static Map<String, String> infer(List<ProductParameters> params, Map<Integer, Product> products) {
                Map<String, String> res = new LinkedHashMap<>();
                List<String> p1 = values(params, 1);
                List<String> p2 = values(params, 2);
                List<String> p3 = values(params, 3);
                List<String> p4 = values(params, 4);
                List<String> p5 = values(params, 5);

                int total = params.size();
                double cov4 = p4.size() / (double) total;
                double cov5 = p5.size() / (double) total;

                boolean looksDims = looksLikeDimsRelaxed(p1, p2, p3) || namesSuggestDimsLoose(products);
                if (looksDims) {
                    res.put("param1", "Длина");
                    res.put("param2", "Ширина");
                    res.put("param3", "Высота");
                }

                if (p1.isEmpty()) res.put("param1", "");
                if (p2.isEmpty()) res.put("param2", "");
                if (p3.isEmpty()) res.put("param3", "");

                if (cov4 < COVERAGE_THRESHOLD) res.put("param4", "");
                if (cov5 < COVERAGE_THRESHOLD) res.put("param5", "");

                inferSlot("param1", p1, res);
                inferSlot("param2", p2, res);
                inferSlot("param3", p3, res);
                inferSlot("param4", p4, res);
                inferSlot("param5", p5, res);

                if ("Толщина".equals(res.get("param4"))) res.put("param4", "Толщина, мм");
                if ("Ток (А)".equals(res.get("param2"))) res.put("param2", "Ток, А");
                if ("Отсечка (кА)".equals(res.get("param4"))) res.put("param4", "Отключающая способность, кА");

                return res;
            }

            private static boolean looksLikeDimsRelaxed(List<String> p1, List<String> p2, List<String> p3) {
                int ok = 0;
                if (percentNumeric(p1) > 0.6) ok++;
                if (percentNumeric(p2) > 0.6) ok++;
                if (percentNumeric(p3) > 0.4) ok++;
                return ok >= 2;
            }

            private static boolean namesSuggestDimsLoose(Map<Integer, Product> products) {
                if (products.isEmpty()) return false;
                long hits = products.values().stream()
                        .map(p -> Optional.ofNullable(p.getName()).orElse(""))
                        .filter(n -> DIM_KEYWORDS.matcher(n).find())
                        .count();
                return (hits / (double) products.size()) > 0.05;
            }

            private static List<String> values(List<ProductParameters> params, int idx) {
                Stream<String> s = params.stream().map(pp -> switch (idx) {
                    case 1 -> pp.getParam1();
                    case 2 -> pp.getParam2();
                    case 3 -> pp.getParam3();
                    case 4 -> pp.getParam4();
                    case 5 -> pp.getParam5();
                    default -> null;
                });
                return s.filter(Objects::nonNull).map(String::trim).filter(v -> !v.isEmpty()).collect(toList());
            }

            private static void inferSlot(String key, List<String> values, Map<String, String> out) {
                if (out.containsKey(key)) return;
                if (values.isEmpty()) { out.put(key, ""); return; }

                long poles   = values.stream().filter(v -> POLES.matcher(v).matches()).count();
                long current = values.stream().filter(v -> CURRENT_A.matcher(v).matches()).count();
                long chr     = values.stream().filter(v -> CHAR_BCD.matcher(v).matches()).count();
                long ka      = values.stream().filter(v -> KA_CUTOFF.matcher(v).matches()).count();
                long thick   = values.stream().filter(v -> THICKNESS.matcher(v).matches()).count();
                long nums    = values.stream().filter(v -> NUMBER.matcher(v).matches()).count();

                long max = Collections.max(Arrays.asList(poles, current, chr, ka, thick, nums));
                if (max == 0) { out.put(key, ""); return; }
                if (max == poles)   { out.put(key, "Полюсность"); return; }
                if (max == current) { out.put(key, "Ток (А)"); return; }
                if (max == chr)     { out.put(key, "Характеристика (B/C/D)"); return; }
                if (max == ka)      { out.put(key, "Отсечка (кА)"); return; }
                if (max == thick)   { out.put(key, "Толщина"); return; }
                out.put(key, "Размер");
            }

            private static double percentNumeric(List<String> vals) {
                if (vals.isEmpty()) return 0;
                long m = vals.stream().filter(v -> NUMBER.matcher(v).matches()).count();
                return m / (double) vals.size();
            }
        }
    }

    // ===== helpers =====

    private static String rfc5987ContentDisposition(String filenameUtf8) {
        String safeAscii = filenameUtf8.replace('"', '\'')
                .replaceAll("[^A-Za-z0-9._-]", "_");
        String encoded = URLEncoder.encode(filenameUtf8, StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + safeAscii + "\"; filename*=UTF-8''" + encoded;
    }

    @SuppressWarnings("unchecked")
    private Map<Long, String> ensureSections(HttpSession s) {
        Map<Long, String> sec = (Map<Long, String>) s.getAttribute("sections");
        if (sec == null) {
            sec = new LinkedHashMap<>();
            sec.put(1L, "Общий");
            s.setAttribute("sections", sec);
        }
        return sec;
    }

    @SuppressWarnings("unchecked")
    private void ensureSectionMaps(HttpSession s) {
        if (s.getAttribute("sections") == null) {
            Map<Long,String> sec = new LinkedHashMap<>();
            sec.put(1L, "Общий");
            s.setAttribute("sections", sec);
        }
        if (s.getAttribute("sectionParent") == null) {
            Map<Long,Long> parent = new HashMap<>();
            parent.put(1L, null);
            s.setAttribute("sectionParent", parent);
        }
        if (s.getAttribute("productSection") == null) {
            s.setAttribute("productSection", new HashMap<Integer,Long>());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Long> ensureProductSection(HttpSession s) {
        Map<Integer, Long> map = (Map<Integer, Long>) s.getAttribute("productSection");
        if (map == null) {
            map = new HashMap<>();
            s.setAttribute("productSection", map);
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, Map<Long,Integer>> ensureProductSectionQty(HttpSession s) {
        Map<Integer, Map<Long,Integer>> m = (Map<Integer, Map<Long,Integer>>) s.getAttribute(PRODUCT_SECTION_QTY);
        if (m == null) {
            m = new HashMap<>();
            s.setAttribute(PRODUCT_SECTION_QTY, m);
        }
        return m;
    }

    @GetMapping("/cart/sections/splits")
    @ResponseBody
    public Map<Integer, Map<Long, Integer>> sectionSplits(HttpSession s) {
        return ensureProductSectionQty(s);
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Long> ensureSectionParents(HttpSession s) {
        Map<Long, Long> parents = (Map<Long, Long>) s.getAttribute("sectionParent");
        if (parents == null) {
            parents = new HashMap<>();
            parents.put(1L, null);
            s.setAttribute("sectionParent", parents);
        }
        Map<Long, Long> legacy = (Map<Long, Long>) s.getAttribute("sectionParents");
        if (legacy != null && !legacy.isEmpty()) {
            parents.clear();
            parents.putAll(legacy);
            s.removeAttribute("sectionParents");
        }
        return parents;
    }

    private Long findRootId(HttpSession s) {
        Map<Long, Long> parents = ensureSectionParents(s);
        return parents.entrySet().stream()
                .filter(e -> e.getValue() == null)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(1L);
    }

    @PostMapping("/cart/sections/extract-one")
    @ResponseBody
    public Map<String,Object> extractOne(@RequestParam Integer productId,
                                         @RequestParam(required=false) Long fromSectionId,
                                         @RequestParam Long toSectionId,
                                         @RequestParam(defaultValue="1") Integer qty,
                                         HttpSession s) {
        if (qty == null || qty <= 0) qty = 1;

        @SuppressWarnings("unchecked")
        Map<Integer,Integer> cart = (Map<Integer,Integer>) s.getAttribute("cart");
        if (cart == null || !cart.containsKey(productId))
            return Map.of("ok", false, "msg", "Товара нет в корзине");

        int totalQty = cart.get(productId);
        Map<Integer, Map<Long,Integer>> splits = ensureProductSectionQty(s);
        Map<Long,Integer> bySection = splits.computeIfAbsent(productId, k -> new HashMap<>());

        Map<Integer, Long> prodSec = ensureProductSection(s);
        Long from = (fromSectionId != null) ? fromSectionId : prodSec.getOrDefault(productId, findRootId(s));

        int assignedInFrom = bySection.getOrDefault(from, 0);
        int assignedTotal  = bySection.values().stream().mapToInt(Integer::intValue).sum();
        int free           = Math.max(0, totalQty - assignedTotal);

        int need = qty;

        int fromTaken = Math.min(assignedInFrom, need);
        if (fromTaken > 0) bySection.put(from, assignedInFrom - fromTaken);
        need -= fromTaken;

        int freeTaken = Math.min(free, need);
        need -= freeTaken;

        if (need > 0) {
            if (fromTaken > 0) bySection.put(from, assignedInFrom);
            return Map.of("ok", false, "msg", "Недостаточно количества для извлечения");
        }

        bySection.merge(toSectionId, qty, Integer::sum);
        bySection.entrySet().removeIf(e -> e.getValue() == null || e.getValue() <= 0);
        if (bySection.isEmpty()) splits.remove(productId);

        int max = -1; Long dom = prodSec.getOrDefault(productId, 1L);
        for (var e : bySection.entrySet()) {
            if (e.getValue() > max) { max = e.getValue(); dom = e.getKey(); }
        }
        prodSec.put(productId, dom);

        return Map.of("ok", true, "splits", bySection);
    }
}
