package com.example.productfilter.controller;

import com.example.productfilter.dto.ProductFilterDTO;
import com.example.productfilter.dto.ProposalHistoryView;
import com.example.productfilter.model.*;
import com.example.productfilter.repository.*;
import com.example.productfilter.repository.spec.ProductSpecs;
import com.example.productfilter.service.CartStore;
import com.example.productfilter.service.ExcelImportWithSmartParserService;
import com.example.productfilter.service.ProductFilterService;
import com.example.productfilter.service.ProposalService;
import com.example.productfilter.util.FileNames;
import com.example.productfilter.util.search.SearchNormalizer;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
import java.math.BigDecimal;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Controller
public class ProductFilterController {
    @Autowired
    private BrandRepository brandRepo;
    @Autowired
    private CategoryRepository categoryRepo;
    @Autowired
    private ProductRepository productRepo;
    @Autowired
    private ProductParameterRepository parameterRepo;
    @Autowired
    private ProductCategoriesRepository productCategoriesRepo;
    @Autowired
    private ProposalRepository proposalRepo;
    @Autowired
    private ProposalSectionRepository proposalSectionRepo;
    @Autowired
    private ProductFilterService productFilterService;
    private final SearchNormalizer normalizer;
    private final CartStore cartStore;

    private final ProposalService proposalService;
    private final ExcelImportWithSmartParserService excelImportWithSmartParserService;

    private static final String PRODUCT_SECTION_QTY = "productSectionQty"; // Map<Integer, Map<Long,Integer>>
    private static final Logger logger = LoggerFactory.getLogger(ProductFilterController.class);
    private static final Logger log = LoggerFactory.getLogger(CartVirtualController.class);


    public ProductFilterController(ProposalService proposalService,
                                   ExcelImportWithSmartParserService excelImportWithSmartParserService, SearchNormalizer normalizer, CartStore cartStore) {
        this.proposalService = proposalService;
        this.excelImportWithSmartParserService = excelImportWithSmartParserService;
        this.normalizer = normalizer;
        this.cartStore = cartStore;
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

        ProductFilterDTO f = new ProductFilterDTO(
                brandId, null, null,
                null, null, null, null, null,
                null
        );

        Set<Integer> ids = productFilterService.resolveProductIds(f);

        Map<String, Object> response = new HashMap<>();
        response.put("groups", categoryRepo.findParentCategoriesByProducts(ids));

        List<ProductParameters> params = parameterRepo.findByProduct_ProductIdIn(ids);
        response.put("param1List",
                params.stream()
                        .map(ProductParameters::getParam1)
                        .filter(Objects::nonNull)
                        .collect(toSet())
        );

        return response;
    }


    @GetMapping("/filter/groups")
    @ResponseBody
    public List<Category> groups(@ModelAttribute ProductFilterDTO f) {

        boolean noFilters =
                f.brandId() == null &&
                        f.groupId() == null &&
                        f.subGroupId() == null &&
                        (f.param1() == null || f.param1().isBlank()) &&
                        (f.param2() == null || f.param2().isBlank()) &&
                        (f.param3() == null || f.param3().isBlank()) &&
                        (f.param4() == null || f.param4().isBlank()) &&
                        (f.param5() == null || f.param5().isBlank()) &&
                        (f.keyword() == null || f.keyword().isBlank());

        if (noFilters) {
            return categoryRepo.findByParentCategoryIdIsNull();
        }

        Set<Integer> ids = productFilterService.resolveProductIds(f);
        if (ids == null || ids.isEmpty()) {
            return categoryRepo.findByParentCategoryIdIsNull();
        }

        return categoryRepo.findParentCategoriesByProducts(ids);
    }
    @GetMapping("/filter/groups/all")
    @ResponseBody
    public List<Category> allGroups() {
        // все "родительские" категории (группы)
        return categoryRepo.findByParentCategoryIdIsNull();
    }


    @GetMapping("/filter/subgroups")
    @ResponseBody
    public List<Category> subGroups(@ModelAttribute ProductFilterDTO f) {

        Integer groupId = f.groupId();

        // КЛЮЧЕВОЕ: если группа не выбрана, но выбрана подгруппа — вычисляем группу по подгруппе
        if (groupId == null && f.subGroupId() != null) {
            groupId = categoryRepo.findParentIdByCategoryId(f.subGroupId());
        }

        // Если группа известна — возвращаем подгруппы этой группы (и выбранная точно будет в списке)
        if (groupId != null) {
            return categoryRepo.findByParentCategoryIdOrderByNameAsc(groupId);
        }

        // Иначе работаем как раньше: по товарам / либо все подгруппы
        Set<Integer> ids = productFilterService.resolveProductIds(f);

        if (ids == null || ids.isEmpty()) {
            return categoryRepo.findAllSubGroupsOrderByNameAsc();
        }

        return categoryRepo.findSubCategoriesByProducts(ids);
    }





    @GetMapping("/filter/parameters")
    @ResponseBody
    public Map<String, Set<String>> parameters(@ModelAttribute ProductFilterDTO f) {

        boolean emptyCtx =
                f.brandId() == null &&
                        f.groupId() == null &&
                        f.subGroupId() == null &&
                        (f.keyword() == null || f.keyword().isBlank());

        // FIX: если нет контекста — как на главной (глобальные distinct)
        if (emptyCtx) {
            return Map.of(
                    "param1List", new LinkedHashSet<>(parameterRepo.findDistinctParam1()),
                    "param2List", new LinkedHashSet<>(parameterRepo.findDistinctParam2()),
                    "param3List", new LinkedHashSet<>(parameterRepo.findDistinctParam3()),
                    "param4List", new LinkedHashSet<>(parameterRepo.findDistinctParam4()),
                    "param5List", new LinkedHashSet<>(parameterRepo.findDistinctParam5())
            );
        }

        // ОПЦИИ считаем по контексту, а не по уже выбранным параметрам
        ProductFilterDTO optionsCtx = new ProductFilterDTO(
                f.brandId(),
                f.groupId(),
                f.subGroupId(),
                null, null, null, null, null,
                f.keyword()
        );

        Set<Integer> ids = productFilterService.resolveProductIds(optionsCtx);
        if (ids == null || ids.isEmpty()) {
            return Map.of(
                    "param1List", Set.of(),
                    "param2List", Set.of(),
                    "param3List", Set.of(),
                    "param4List", Set.of(),
                    "param5List", Set.of()
            );
        }

        List<ProductParameters> params = parameterRepo.findByProduct_ProductIdIn(ids);

        return Map.of(
                "param1List", params.stream().map(ProductParameters::getParam1).filter(Objects::nonNull).collect(toSet()),
                "param2List", params.stream().map(ProductParameters::getParam2).filter(Objects::nonNull).collect(toSet()),
                "param3List", params.stream().map(ProductParameters::getParam3).filter(Objects::nonNull).collect(toSet()),
                "param4List", params.stream().map(ProductParameters::getParam4).filter(Objects::nonNull).collect(toSet()),
                "param5List", params.stream().map(ProductParameters::getParam5).filter(Objects::nonNull).collect(toSet())
        );
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

        if (param1 != null && param1.isBlank()) param1 = null;
        if (param2 != null && param2.isBlank()) param2 = null;
        if (param3 != null && param3.isBlank()) param3 = null;
        if (param4 != null && param4.isBlank()) param4 = null;
        if (param5 != null && param5.isBlank()) param5 = null;
        if (keyword != null && keyword.isBlank()) keyword = null;

        Integer uiGroupId = groupId;
        if (uiGroupId == null && subGroupId != null) {
            uiGroupId = categoryRepo.findParentIdByCategoryId(subGroupId);
        }

        boolean noFilters =
                brandId == null &&
                        groupId == null &&
                        subGroupId == null &&
                        param1 == null && param2 == null && param3 == null && param4 == null && param5 == null &&
                        keyword == null;

        // DTO ТОЛЬКО ДЛЯ СТРУКТУРЫ (без keyword!)
        ProductFilterDTO idsCtx = new ProductFilterDTO(
                brandId,
                groupId,
                subGroupId,
                param1, param2, param3, param4, param5,
                null   // <-- КЛЮЧЕВОЕ
        );

        Set<Integer> ids;
        if (noFilters) {
            ids = productRepo.findAll()
                    .stream()
                    .map(Product::getProductId)
                    .collect(Collectors.toSet());
        } else {
            ids = productFilterService.resolveProductIds(idsCtx);
        }

        List<Product> products;

        if (ids == null || ids.isEmpty()) {
            products = new ArrayList<>();
        } else {
            products = new ArrayList<>(
                    productRepo.findAll(
                            Specification
                                    .where(ProductSpecs.idIn(ids))
                                    .and(ProductSpecs.keywordElastic(keyword, normalizer))
                    )
            );
        }

        // ===== SORT =====
        if (sort != null && !sort.isEmpty()) {
            String[] sorts = sort.split(",");
            for (String s : sorts) {
                switch (s) {
                    case "priceAsc" ->
                            products.sort(Comparator.comparing(Product::getPrice, Comparator.nullsLast(BigDecimal::compareTo)));
                    case "priceDesc" ->
                            products.sort(Comparator.comparing(Product::getPrice, Comparator.nullsLast(BigDecimal::compareTo)).reversed());
                    case "nameAsc" ->
                            products.sort(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER));
                    case "nameDesc" ->
                            products.sort(Comparator.comparing(Product::getName, String.CASE_INSENSITIVE_ORDER).reversed());
                    case "brandAsc" ->
                            products.sort(Comparator.comparing(p -> p.getBrand().getBrandName(), String.CASE_INSENSITIVE_ORDER));
                    case "brandDesc" ->
                            products.sort(Comparator.comparing((Product p) -> p.getBrand().getBrandName(), String.CASE_INSENSITIVE_ORDER).reversed());
                }
            }
        }

        // ===== PAGINATION =====
        int total = products.size();
        int totalPages = (int) Math.ceil(total / (double) pageSize);
        if (totalPages == 0) totalPages = 1;

        if (page < 0) page = 0;
        if (page > totalPages - 1) page = totalPages - 1;

        int start = page * pageSize;
        int end = Math.min(start + pageSize, total);

        products.sort(Comparator.comparing(Product::getProductId));

        List<Product> pageContent =
                (start < end) ? new ArrayList<>(products.subList(start, end)) : List.of();

        model.addAttribute("products", pageContent);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("hasPrev", page > 0);
        model.addAttribute("hasNext", page < totalPages - 1);

        model.addAttribute("brands", brandRepo.findAll());
        model.addAttribute("groups", categoryRepo.findByParentCategoryIdIsNull());
        model.addAttribute("subGroups",
                uiGroupId != null
                        ? categoryRepo.findByParentCategoryIdOrderByNameAsc(uiGroupId)
                        : categoryRepo.findAllSubGroupsOrderByNameAsc()
        );


        Set<Integer> idsForParams = noFilters ? null : ids;

        if (noFilters) {
            model.addAttribute("param1List", parameterRepo.findDistinctParam1());
            model.addAttribute("param2List", parameterRepo.findDistinctParam2());
            model.addAttribute("param3List", parameterRepo.findDistinctParam3());
            model.addAttribute("param4List", parameterRepo.findDistinctParam4());
            model.addAttribute("param5List", parameterRepo.findDistinctParam5());
        } else {
            List<ProductParameters> params =
                    (idsForParams == null || idsForParams.isEmpty())
                            ? List.of()
                            : parameterRepo.findByProduct_ProductIdIn(idsForParams);

            model.addAttribute("param1List", params.stream().map(ProductParameters::getParam1).filter(Objects::nonNull).collect(toSet()));
            model.addAttribute("param2List", params.stream().map(ProductParameters::getParam2).filter(Objects::nonNull).collect(toSet()));
            model.addAttribute("param3List", params.stream().map(ProductParameters::getParam3).filter(Objects::nonNull).collect(toSet()));
            model.addAttribute("param4List", params.stream().map(ProductParameters::getParam4).filter(Objects::nonNull).collect(toSet()));
            model.addAttribute("param5List", params.stream().map(ProductParameters::getParam5).filter(Objects::nonNull).collect(toSet()));
        }


        Map<String, Object> selectedParams = new HashMap<>();
        selectedParams.put("brandId", brandId);
        selectedParams.put("groupId", groupId);
        selectedParams.put("subGroupId", subGroupId);
        selectedParams.put("param1", param1);
        selectedParams.put("param2", param2);
        selectedParams.put("param3", param3);
        selectedParams.put("param4", param4);
        selectedParams.put("param5", param5);
        selectedParams.put("keyword", keyword);
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

        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("cart");
        if (cart == null) cart = new HashMap<>();
        cart.put(productId, cart.getOrDefault(productId, 0) + 1);
        session.setAttribute("cart", cart);

        Map<Long, String> sec = ensureSections(session);
        Long sid = (sectionId != null && sec.containsKey(sectionId)) ? sectionId : 1L;

        Map<Integer, Long> productSection = ensureProductSection(session);
        productSection.putIfAbsent(productId, sid);   // ✅ фикс
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

    @PostMapping("/cart/set-qty")
    @ResponseBody
    public Map<String, Object> setQuantity(@RequestParam Integer productId,
                                           @RequestParam Integer qty,
                                           HttpSession s) {
        if (productId == null)
            return Map.of("ok", false, "msg", "Нет productId");

        @SuppressWarnings("unchecked")
        Map<Integer, Integer> cart = (Map<Integer, Integer>) s.getAttribute("cart");
        if (cart == null || !cart.containsKey(productId)) {
            return Map.of("ok", false, "msg", "Товар не в корзине");
        }

        // ===== 🔥 УДАЛЕНИЕ ТОВАРА ПРИ qty <= 0 =====
        if (qty == null || qty <= 0) {
            cart.remove(productId);
            s.setAttribute("cart", cart);

            ensureProductSection(s).remove(productId);
            ensureProductSectionQty(s).remove(productId);

            Map<Integer, Double> coeff =
                    (Map<Integer, Double>) s.getAttribute("coefficientMap");
            if (coeff != null) coeff.remove(productId);

            // удалить виртуальный, если это виртуальный
            Object scoped = s.getAttribute("scopedTarget.cartStore");
            if (scoped instanceof com.example.productfilter.service.CartStore cs) {
                cs.remove(productId);
            }

            return Map.of(
                    "ok", true,
                    "removed", true,
                    "productId", productId
            );
        }

        // ===== если qty > 0 — обычная логика =====
        cart.put(productId, qty);
        s.setAttribute("cart", cart);


        // ужать разбиения по секциям, если они больше нового qty
        Map<Integer, Map<Long, Integer>> splits = ensureProductSectionQty(s);
        Map<Long, Integer> bySection = splits.get(productId);
        if (bySection != null && !bySection.isEmpty()) {
            int assigned = bySection.values().stream().mapToInt(v -> v == null ? 0 : v).sum();
            if (assigned > qty) {
                int rest = qty;
                Long dominant = bySection.entrySet().stream()
                        .max(Comparator.comparingInt(e -> e.getValue() == null ? 0 : e.getValue()))
                        .map(Map.Entry::getKey).orElse(null);
                Map<Long, Integer> resized = new LinkedHashMap<>();
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
                if (resized.isEmpty()) splits.remove(productId);
                else splits.put(productId, resized);
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
            @SuppressWarnings("unchecked")
            Map<?, Double> baseOverrideRaw = (Map<?, Double>) s.getAttribute("priceOverrideBaseMap");
            Map<Integer, Double> baseOverride = normalizeMapDouble(baseOverrideRaw);


            double base = resolveBasePrice(p, p.getProductId(), baseOverride);
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

    // =========================
// /cart (FIXED)
// =========================
    // ВСТАВЬ ЭТИ МЕТОДЫ В ТВОЙ СУЩЕСТВУЮЩИЙ КОНТРОЛЛЕР (где уже есть productRepo/parameterRepo/...)
// НИЧЕГО НЕ ВЫНОСИМ В CartController. Это просто готовые методы + хелперы.

    @GetMapping("/cart")
    public String viewCart(Model model, HttpSession session, HttpServletResponse resp) {
        resp.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        resp.setHeader("Pragma", "no-cache");

        String sessionId = session.getId();

        @SuppressWarnings("unchecked")
        Map<?, Integer> cartRaw = (Map<?, Integer>) session.getAttribute("cart");
        @SuppressWarnings("unchecked")
        Map<?, Double> coeffRaw = (Map<?, Double>) session.getAttribute("coefficientMap");

        if (cartRaw == null) cartRaw = new HashMap<>();
        if (coeffRaw == null) coeffRaw = new HashMap<>();

        Map<Integer, Integer> cart = normalizeMapInt(cartRaw);
        Map<Integer, Double> coefficientMap = normalizeMapDouble(coeffRaw);

        session.setAttribute("cart", cart);
        session.setAttribute("coefficientMap", coefficientMap);

        List<Product> products = (!cart.isEmpty())
                ? productRepo.findAllById(cart.keySet())
                : List.of();

        // ---- БАЗОВЫЙ override ТОЛЬКО тут, отдельным ключом ----
        // Новый источник базы: priceOverrideBaseMap
        // Миграция: если раньше финал случайно писалcя в priceOverrideMap / proposalPriceOverrideMap — починим
        Map<Integer, Double> priceOverrideBase = loadAndSanitizeBaseOverride(session, products, coefficientMap);
        session.setAttribute("priceOverrideBaseMap", priceOverrideBase);

        // ---- ВЫЧИСЛЯЕМЫЕ карты ----
        Map<Integer, Double> baseUnitPriceMap = new HashMap<>(); // base
        Map<Integer, Double> unitPriceMap = new HashMap<>();     // base*coeff
        Map<Integer, Double> lineSumMap = new HashMap<>();       // unit*qty

        double totalSum = 0.0;

        for (Product p : products) {
            Integer pid = p.getProductId();
            int qty = cart.getOrDefault(pid, 1);

            double base = resolveBasePrice(p, pid, priceOverrideBase);
            double coeff = coefficientMap.getOrDefault(pid, 1.0);

            double finalUnit = base * coeff;
            double lineSum = finalUnit * qty;

            baseUnitPriceMap.put(pid, base);
            unitPriceMap.put(pid, finalUnit);
            lineSumMap.put(pid, lineSum);

            totalSum += lineSum;
        }

        session.setAttribute("baseUnitPriceMap", baseUnitPriceMap);
        session.setAttribute("unitPriceMap", unitPriceMap);
        session.setAttribute("lineSumMap", lineSumMap);

        Set<Integer> pids = products.stream().map(Product::getProductId).collect(Collectors.toSet());

        List<ProductParameters> parameters = pids.isEmpty()
                ? List.of()
                : parameterRepo.findByProduct_ProductIdIn(pids);

        Map<Integer, ProductParameters> paramsByProduct = new HashMap<>();
        for (ProductParameters pp : parameters) {
            if (pp.getProduct() != null && pp.getProduct().getProductId() != null) {
                paramsByProduct.put(pp.getProduct().getProductId(), pp);
            }
        }
        model.addAttribute("paramsByProduct", paramsByProduct);

        Map<Integer, List<Category>> productIdToCategories = new HashMap<>();
        if (!pids.isEmpty()) {
            List<ProductCategories> links = productCategoriesRepo.findByProductIdIn(pids);

            Set<Integer> categoryIds = links.stream()
                    .map(ProductCategories::getCategoryId)
                    .collect(Collectors.toSet());

            List<Category> allCategories = categoryRepo.findAllById(categoryIds);

            Map<Integer, Category> categoryMap = allCategories.stream()
                    .collect(Collectors.toMap(Category::getCategoryId, c -> c));

            for (ProductCategories link : links) {
                Integer productId = link.getProductId();
                Integer categoryId = link.getCategoryId();
                Category category = categoryMap.get(categoryId);
                if (category != null) {
                    productIdToCategories.computeIfAbsent(productId, k -> new ArrayList<>()).add(category);
                }
            }
        }
        model.addAttribute("productCategoriesMap", productIdToCategories);

        @SuppressWarnings("unchecked")
        Map<String, Object> lastFilters = (Map<String, Object>) session.getAttribute("lastFilters");
        model.addAttribute("filterParams", lastFilters != null ? lastFilters : new HashMap<>());

        int totalQuantity = cart.values().stream().mapToInt(Integer::intValue).sum();

        model.addAttribute("cartProducts", products);
        model.addAttribute("cartParams", parameters);
        model.addAttribute("quantities", cart);
        model.addAttribute("coefficientMap", coefficientMap);

        // В шаблоне:
        // baseUnitPriceMap[pid] — базовая цена
        // unitPriceMap[pid] — цена с коэффициентом
        // lineSumMap[pid] — сумма строки
        model.addAttribute("baseUnitPriceMap", baseUnitPriceMap);
        model.addAttribute("unitPriceMap", unitPriceMap);
        model.addAttribute("lineSumMap", lineSumMap);

        model.addAttribute("totalSum", totalSum);
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

        @SuppressWarnings("unchecked")
        Map<?, Double> coeffRaw = (Map<?, Double>) session.getAttribute("coefficientMap");
        Map<Integer, Double> coefficientMap = normalizeMapDouble(coeffRaw);

        coefficientMap.put(productId, coefficient != null ? coefficient : 1.0);
        session.setAttribute("coefficientMap", coefficientMap);

        // важно: вычисляемые карты НЕ должны жить как "источник правды"
        session.removeAttribute("baseUnitPriceMap");
        session.removeAttribute("unitPriceMap");
        session.removeAttribute("lineSumMap");
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
            Map<Integer, Map<Long, Integer>> splits = ensureProductSectionQty(session);
            splits.remove(productId);
        }
        session.setAttribute("cart", cart);
    }

    @PostMapping("/cart/confirm")
    public String confirmCart(HttpSession session, Model model) {
        @SuppressWarnings("unchecked")
        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("cart");
        if (cart == null || cart.isEmpty()) return "redirect:/cart";

        List<Product> products = productRepo.findAllById(cart.keySet());

        @SuppressWarnings("unchecked")
        Map<?, Double> coeffRaw = (Map<?, Double>) session.getAttribute("coefficientMap");
        Map<Integer, Double> coefficientMap = normalizeMapDouble(coeffRaw);

        // БАЗОВЫЙ override берём ТОЛЬКО из priceOverrideBaseMap
        @SuppressWarnings("unchecked")
        Map<?, Double> baseOverrideRaw = (Map<?, Double>) session.getAttribute("priceOverrideBaseMap");
        Map<Integer, Double> priceOverrideBase = normalizeMapDouble(baseOverrideRaw);

        Map<Integer, Double> finalPrices = new HashMap<>();
        Map<Integer, Double> totalSums = new HashMap<>();
        double totalSum = 0.0;

        for (Product product : products) {
            Integer pid = product.getProductId();
            int qty = cart.getOrDefault(pid, 1);

            double base = resolveBasePrice(product, pid, priceOverrideBase);
            double coeff = coefficientMap.getOrDefault(pid, 1.0);

            double finalPrice = base * coeff;
            double total = finalPrice * qty;

            finalPrices.put(pid, finalPrice);
            totalSums.put(pid, total);
            totalSum += total;
        }

        List<ProductParameters> parameters = parameterRepo.findByProduct_ProductIdIn(cart.keySet());
        Map<Integer, ProductParameters> paramMap = parameters.stream()
                .filter(p -> p.getProduct() != null && p.getProduct().getProductId() != null)
                .collect(Collectors.toMap(p -> p.getProduct().getProductId(), Function.identity()));

        model.addAttribute("products", products);
        model.addAttribute("quantities", cart);
        model.addAttribute("coefficientMap", coefficientMap);
        model.addAttribute("finalPrices", finalPrices);
        model.addAttribute("totalSums", totalSums);
        model.addAttribute("paramMap", paramMap);
        model.addAttribute("totalSum", totalSum);
        model.addAttribute("totalQuantity", cart.values().stream().mapToInt(i -> i).sum());

        // В сессию для генераторов:
        session.setAttribute("proposalCart", new HashMap<>(cart));
        session.setAttribute("proposalCoefficients", new HashMap<>(coefficientMap));

        // КЛЮЧЕВОЕ: сюда кладём БАЗУ, не finalPrices
        session.setAttribute("proposalPriceOverrideMap", new HashMap<>(priceOverrideBase));

        // при желании отдельно можно хранить finalPrices:
        session.setAttribute("proposalFinalPrices", new HashMap<>(finalPrices));

        session.setAttribute("proposalProducts", products);
        session.setAttribute("proposalTotal", totalSum);

        @SuppressWarnings("unchecked")
        Map<String, Object> lastFilters = (Map<String, Object>) session.getAttribute("lastFilters");
        model.addAttribute("filterParams", lastFilters != null ? lastFilters : new HashMap<>());

        return "proposal";
    }


// =========================
// HELPERS (добавь в тот же контроллер ниже методов)
// =========================

    // ===== HELPERS (добавь в этот же контроллер) =====
// ===== HELPERS =====

    private Map<Integer, Integer> normalizeMapInt(Object raw) {
        Map<Integer, Integer> out = new HashMap<>();
        if (!(raw instanceof Map<?, ?> map)) return out;

        for (Map.Entry<?, ?> e : map.entrySet()) {
            Integer key = toIntKey(e.getKey());
            if (key == null) continue;

            Object v = e.getValue();
            Integer val;
            if (v == null) val = 0;
            else if (v instanceof Number n) val = n.intValue();
            else val = Integer.valueOf(v.toString());

            out.put(key, val);
        }
        return out;
    }

    private Map<Integer, Double> normalizeMapDouble(Object raw) {
        Map<Integer, Double> out = new HashMap<>();
        if (!(raw instanceof Map<?, ?> map)) return out;

        for (Map.Entry<?, ?> e : map.entrySet()) {
            Integer key = toIntKey(e.getKey());
            if (key == null) continue;

            Object v = e.getValue();
            Double val;
            if (v == null) val = null;
            else if (v instanceof Number n) val = n.doubleValue();
            else val = Double.valueOf(v.toString());

            out.put(key, val);
        }
        return out;
    }

    private Integer toIntKey(Object k) {
        if (k == null) return null;
        if (k instanceof Integer i) return i;
        if (k instanceof Number n) return n.intValue();
        String s = k.toString().trim();
        if (s.isEmpty()) return null;
        return Integer.valueOf(s);
    }

    private double resolveBasePrice(Product p, Integer pid, Map<Integer, Double> priceOverrideBase) {
        if (pid != null && priceOverrideBase != null && priceOverrideBase.containsKey(pid)) {
            Double v = priceOverrideBase.get(pid);
            return v != null ? v : 0.0;
        }
        return (p.getPrice() != null ? p.getPrice().doubleValue() : 0.0);
    }

    /**
     * Берёт базовый override из:
     * 1) priceOverrideBaseMap (новый правильный ключ)
     * 2) priceOverrideMap (legacy)
     * 3) proposalPriceOverrideMap (legacy)
     *
     * И санитизирует: если там лежит финал (base*coeff), вернёт base.
     */
    private Map<Integer, Double> loadAndSanitizeBaseOverride(HttpSession session,
                                                             List<Product> products,
                                                             Map<Integer, Double> coefficientMap) {

        // 1) Берём КАНДИДАТ (сначала правильный ключ, потом legacy)
        @SuppressWarnings("unchecked")
        Map<?, Double> rawBase = (Map<?, Double>) session.getAttribute("priceOverrideBaseMap");
        @SuppressWarnings("unchecked")
        Map<?, Double> rawLegacy1 = (Map<?, Double>) session.getAttribute("priceOverrideMap");
        @SuppressWarnings("unchecked")
        Map<?, Double> rawLegacy2 = (Map<?, Double>) session.getAttribute("proposalPriceOverrideMap");

        Map<Integer, Double> candidate = normalizeMapDouble(rawBase);
        boolean fromLegacy = false;

        if (candidate.isEmpty()) {
            candidate = normalizeMapDouble(rawLegacy1);
            fromLegacy = !candidate.isEmpty();
        }
        if (candidate.isEmpty()) {
            candidate = normalizeMapDouble(rawLegacy2);
            fromLegacy = !candidate.isEmpty();
        }

        if (candidate.isEmpty()) return new HashMap<>();

        // 2) productPrice для сравнения
        Map<Integer, Double> productPrice = new HashMap<>();
        for (Product p : products) {
            if (p.getProductId() == null) continue;
            productPrice.put(
                    p.getProductId(),
                    p.getPrice() != null ? p.getPrice().doubleValue() : 0.0
            );
        }

        // 3) Санитизация: если значение выглядит как (productPrice * coeff) — делим на coeff
        Map<Integer, Double> sanitized = new HashMap<>();
        boolean changed = false;

        for (Map.Entry<Integer, Double> e : candidate.entrySet()) {
            Integer pid = e.getKey();
            Double v = e.getValue();

            if (pid == null || v == null) {
                sanitized.put(pid, v);
                continue;
            }

            double coeff = coefficientMap.getOrDefault(pid, 1.0);
            double pBase = productPrice.getOrDefault(pid, 0.0);

            double out = v;

            // эвристика: "похоже на уже-умноженную цену"
            if (coeff != 0.0
                    && Math.abs(coeff - 1.0) > 1e-9
                    && pBase > 0.0
                    && approx(v, pBase * coeff, 0.005)) {
                out = v / coeff;
            }

            sanitized.put(pid, out);
            if (Double.compare(out, v) != 0) changed = true;
        }

        // 4) Зафиксировать в одном месте
        session.setAttribute("priceOverrideBaseMap", sanitized);

        // legacy-ключи лучше убрать, чтобы не мешали
        if (fromLegacy || changed) {
            session.removeAttribute("priceOverrideMap");
            session.removeAttribute("proposalPriceOverrideMap");
        }

        return sanitized;
    }


    private boolean approx(double a, double b, double relTol) {
        double diff = Math.abs(a - b);
        double scale = Math.max(1.0, Math.max(Math.abs(a), Math.abs(b)));
        return diff / scale <= relTol;
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
                double basePrice = product.getPrice() != null ? product.getPrice().doubleValue() : 0.0;
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

//    @GetMapping("/proposal/excel-kp")
//    public void downloadProposalExcelKp(HttpServletResponse response, HttpSession session) throws IOException {
//        String projectName = Optional.ofNullable((String) session.getAttribute("projectName")).orElse("Проект");
//        String user = FileNames.currentUser();
//        String fnameXlsx = FileNames.kpXlsx(projectName, user);
//
//        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("proposalCart");
//        List<Product> products = (List<Product>) session.getAttribute("proposalProducts");
//        Map<Integer, Double> coefficientMap = (Map<Integer, Double>) session.getAttribute("proposalCoefficients");
//        if (cart == null || products == null || cart.isEmpty()) { response.sendRedirect("/cart"); return; }
//        if (coefficientMap == null) coefficientMap = new HashMap<>();
//
//        ByteArrayOutputStream baos = new ByteArrayOutputStream();
//        try (Workbook workbook = new XSSFWorkbook()) {
//            Sheet sheet = workbook.createSheet("Коммерческое предложение");
//            sheet.setDefaultColumnWidth(20);
//
//            DataFormat df = workbook.createDataFormat();
//            CellStyle num1 = workbook.createCellStyle(); num1.setDataFormat(df.getFormat("# ##0.0"));
//            CellStyle int0 = workbook.createCellStyle(); int0.setDataFormat(df.getFormat("# ##0"));
//
//            Row header = sheet.createRow(0);
//            String[] columns = {"№","Наименование","Артикул","Бренд","Кол-во","Цена","Сумма"};
//            for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);
//
//            int rowIdx = 1, index = 1, totalQty = 0;
//            double totalSum = 0.0;
//
//            for (Product p : products) {
//                int qty = cart.getOrDefault(p.getProductId(), 1);
//                double basePrice = p.getPrice() != null ? p.getPrice() : 0.0;
//                double coeff = coefficientMap.getOrDefault(p.getProductId(), 1.0);
//                double price = basePrice * coeff;
//                double sum = price * qty;
//
//                Row r = sheet.createRow(rowIdx++);
//                r.createCell(0).setCellValue(index++);
//                r.createCell(1).setCellValue(p.getName());
//                r.createCell(2).setCellValue(p.getArticleCode());
//                r.createCell(3).setCellValue(p.getBrand().getBrandName());
//
//                Cell c;
//                c = r.createCell(4); c.setCellValue(qty);   c.setCellStyle(int0);
//                c = r.createCell(5); c.setCellValue(price); c.setCellStyle(num1);
//                c = r.createCell(6); c.setCellValue(sum);   c.setCellStyle(num1);
//
//                totalQty += qty;
//                totalSum += sum;
//            }
//
//            Row totalRow = sheet.createRow(rowIdx);
//            totalRow.createCell(0).setCellValue("Итого:");
//            sheet.addMergedRegion(new CellRangeAddress(rowIdx, rowIdx, 0, 3));
//            Cell cq = totalRow.createCell(4); cq.setCellValue(totalQty); cq.setCellStyle(int0);
//            Cell cs = totalRow.createCell(6); cs.setCellValue(totalSum); cs.setCellStyle(num1);
//
//            workbook.write(baos);
//        }
//
//        byte[] xlsx = baos.toByteArray();
//        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
//        response.setHeader("Content-Disposition", rfc5987ContentDisposition(fnameXlsx));
//        response.setContentLength(xlsx.length);
//
//        try (OutputStream out = response.getOutputStream()) {
//            out.write(xlsx);
//        }
//    }

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

        Map<Integer, Integer> cart = cartStore.getQuantities();

        if (cart == null || cart.isEmpty()) {
            cart = (Map<Integer, Integer>) session.getAttribute("cart");
        }

        if (cart == null || cart.isEmpty()) {
            response.sendRedirect("/cart");
            return;
        }

        Map<Integer, Double> coefficientMap =
                (Map<Integer, Double>) session.getAttribute("coefficientMap");
        if (coefficientMap == null) coefficientMap = new HashMap<>();

        List<Product> products = productRepo.findAllById(cart.keySet());
        Map<Integer, Product> byId = products.stream()
                .collect(Collectors.toMap(Product::getProductId, p -> p));

        Map<Long, String> sections =
                (Map<Long, String>) session.getAttribute("sections");
        Map<Long, Long> parents =
                (Map<Long, Long>) session.getAttribute("sectionParent");
        Map<Integer, Map<Long,Integer>> splits =
                (Map<Integer, Map<Long,Integer>>) session.getAttribute("productSectionQty");

        Map<Integer, Long> productSection =
                (Map<Integer, Long>) session.getAttribute("productSection");

        if (sections == null) sections = new LinkedHashMap<>();
        if (parents == null) parents = new HashMap<>();
        if (splits == null) splits = new HashMap<>();
        if (productSection == null) productSection = new HashMap<>();

        sections.putIfAbsent(1L, "Общий");
        parents.putIfAbsent(1L, null);

        String fname = "estimate.xlsx";

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"" + fname + "\"");

        try (Workbook wb = new XSSFWorkbook();
             OutputStream out = response.getOutputStream()) {

            Sheet sh = wb.createSheet("Estimate");

            Row header = sh.createRow(0);
            String[] cols = {
                    "TYPE",
                    "SECTION_ID",
                    "PARENT_SECTION_ID",
                    "SECTION_NAME",
                    "PRODUCT_ID",
                    "NAME",
                    "ARTICLE",
                    "BRAND",
                    "QTY",
                    "COEFF",
                    "PRICE",
                    "SUM"
            };

            for (int i = 0; i < cols.length; i++)
                header.createCell(i).setCellValue(cols[i]);

            int r = 1;
            double grandTotal = 0.0;

            for (Long sid : sections.keySet()) {

                Row sec = sh.createRow(r++);
                sec.createCell(0).setCellValue("SECTION");
                sec.createCell(1).setCellValue(sid);
                sec.createCell(2).setCellValue(parents.get(sid) != null ? parents.get(sid) : 0);
                sec.createCell(3).setCellValue(sections.get(sid));

                double sectionSum = 0;

                for (Integer pid : cart.keySet()) {

                    Product p = byId.get(pid);
                    if (p == null) continue;

                    int totalQty = cart.getOrDefault(pid, 0);
                    if (totalQty <= 0) continue;

                    Map<Long,Integer> map = splits.get(pid);
                    int qty = 0;

                    // 1️⃣ есть разбиение — используем его
                    if (map != null && map.containsKey(sid)) {
                        qty = map.get(sid);
                    }
                    else {
                        // 2️⃣ если нет разбиения — берём папку назначения
                        Long assigned = productSection.get(pid);

                        if (assigned == null) assigned = 1L; // fallback Общий

                        if (assigned.equals(sid)) {
                            qty = totalQty;
                        }
                    }

                    if (qty <= 0) continue;

                    @SuppressWarnings("unchecked")
                    Map<Integer, Double> baseOverride =
                            (Map<Integer, Double>) session.getAttribute("priceOverrideBaseMap");

                    double base =
                            (baseOverride != null && baseOverride.containsKey(p.getProductId()))
                                    ? Optional.ofNullable(baseOverride.get(p.getProductId())).orElse(0.0)
                                    : (p.getPrice() != null ? p.getPrice().doubleValue() : 0.0);


                    double k = coefficientMap.getOrDefault(pid, 1.0);
                    double sum = qty * base * k;

                    Row row = sh.createRow(r++);
                    int c = 0;

                    row.createCell(c++).setCellValue("ITEM");
                    row.createCell(c++).setCellValue(sid);
                    row.createCell(c++).setCellValue(parents.get(sid) != null ? parents.get(sid) : 0);
                    row.createCell(c++).setCellValue(sections.get(sid));

                    row.createCell(c++).setCellValue(pid);
                    row.createCell(c++).setCellValue(p.getName());
                    row.createCell(c++).setCellValue(p.getArticleCode() != null ? p.getArticleCode() : "");
                    row.createCell(c++).setCellValue(p.getBrand() != null ? p.getBrand().getBrandName() : "");
                    row.createCell(c++).setCellValue(qty);
                    row.createCell(c++).setCellValue(k);
                    row.createCell(c++).setCellValue(base);
                    row.createCell(c++).setCellValue(sum);

                    sectionSum += sum;
                }

                Row totalRow = sh.createRow(r++);
                totalRow.createCell(3).setCellValue("SECTION TOTAL");
                totalRow.createCell(11).setCellValue(sectionSum);

                grandTotal += sectionSum;
            }

            Row total = sh.createRow(r++);
            total.createCell(3).setCellValue("PROJECT TOTAL");
            total.createCell(11).setCellValue(grandTotal);

            wb.write(out);
        }
    }

    @PostMapping("/cart/upload-structured-estimate")
    public String uploadStructuredEstimate(@RequestParam("file") MultipartFile file,
                                           HttpSession session,
                                           RedirectAttributes ra) {

        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "Файл пустой");
            return "redirect:/cart";
        }

        Map<Integer, Integer> cart = new HashMap<>();
        Map<Integer, Double> coeff = new HashMap<>();
        Map<Integer, Double> priceOverrideBase = new HashMap<>();

        Map<Long,String> sections = new LinkedHashMap<>();
        Map<Long,Long> parents = new HashMap<>();
        Map<Integer,Map<Long,Integer>> splits = new HashMap<>();
        Map<Integer,Long> productSection = new HashMap<>();

        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sh = wb.getSheetAt(0);

            for (int i = 1; i <= sh.getLastRowNum(); i++) {
                Row row = sh.getRow(i);
                if (row == null) continue;

                Cell typeCell = row.getCell(0);
                if (typeCell == null) continue;

                if (typeCell.getCellType() == CellType.BLANK) continue;

                String type = typeCell.getStringCellValue();
                if (type == null || type.isBlank()) continue;

                // ---- SECTION ----
                if ("SECTION".equalsIgnoreCase(type)) {
                    Cell idCell = row.getCell(1);
                    Cell parentCell = row.getCell(2);
                    Cell nameCell = row.getCell(3);

                    if (idCell == null || nameCell == null) continue;

                    long id = (long) idCell.getNumericCellValue();
                    long parent = (parentCell != null ? (long) parentCell.getNumericCellValue() : 0);

                    String name = nameCell.getStringCellValue();

                    sections.put(id, name);
                    parents.put(id, parent == 0 ? null : parent);
                    continue;
                }

                // ---- ITEM ----
                if ("ITEM".equalsIgnoreCase(type)) {

                    Long sid = (long) row.getCell(1).getNumericCellValue();
                    Integer pid = (int) row.getCell(4).getNumericCellValue();

                    Integer qty = (int) row.getCell(8).getNumericCellValue();
                    Double k = row.getCell(9).getNumericCellValue();
                    Double price = row.getCell(10).getNumericCellValue();

                    cart.merge(pid, qty, Integer::sum);
                    coeff.put(pid, k);
                    priceOverrideBase.put(pid, price);

                    splits.computeIfAbsent(pid, x -> new HashMap<>())
                            .merge(sid, qty, Integer::sum);

                    productSection.put(pid, sid);
                }

                // ---- IGNORE TOTAL ROWS ----
            }

        } catch (Exception e) {
            e.printStackTrace();
            ra.addFlashAttribute("error", "Ошибка чтения Excel: " + e.getMessage());
            return "redirect:/cart";
        }

        if (sections.isEmpty()) {
            sections.put(1L, "Общий");
            parents.put(1L, null);
        }

        session.setAttribute("cart", cart);
        session.setAttribute("coefficientMap", coeff);
        session.setAttribute("productSectionQty", splits);
        session.setAttribute("productSection", productSection);
        session.setAttribute("sections", sections);
        session.setAttribute("sectionParent", parents);

        // — фиксируем цены
        session.setAttribute("priceOverrideBaseMap", priceOverrideBase);

        session.removeAttribute("priceOverrideMap");

        ra.addFlashAttribute("message", "Смета успешно загружена");
        return "redirect:/cart";
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

            double basePrice = product.getPrice() != null ? product.getPrice().doubleValue() : 0.0;
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
        private final CategoryRepository categoryRepo;

        public ParameterLabelsController(ProductRepository productRepo,
                                         ProductParameterRepository parameterRepo,
                                         ProductCategoriesRepository productCategoriesRepo,
                                         CategoryRepository categoryRepo) {
            this.productRepo = productRepo;
            this.parameterRepo = parameterRepo;
            this.productCategoriesRepo = productCategoriesRepo;
            this.categoryRepo = categoryRepo;
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

            // --- КЛЮЧЕВОЕ: если выбрана группа, берем товары ИЗ ЕЕ ПОДГРУПП ---
            // если выбрана подгруппа — работаем только по ней
            Set<Integer> categoryIds = new HashSet<>();
            if (subGroupId != null) {
                categoryIds.add(subGroupId);
            } else if (groupId != null) {
                categoryIds.add(groupId);
                // дочерние подгруппы группы
                List<Category> subs = categoryRepo.findByParentCategoryIdOrderByNameAsc(groupId);
                for (Category c : subs) categoryIds.add(c.getCategoryId());
            } else {
                return labels;
            }

            // Можно оптимизировать репозиторием (findProductIdsByCategoryIds), но оставляю совместимо с твоим кодом
            List<ProductCategories> links = productCategoriesRepo.findAll();
            Set<Integer> productIds = links.stream()
                    .filter(pc -> categoryIds.contains(pc.getCategoryId()))
                    .map(ProductCategories::getProductId)
                    .collect(Collectors.toSet());

            if (productIds.isEmpty()) return labels;

            List<ProductParameters> params = parameterRepo.findByProduct_ProductIdIn(productIds);
            if (params.isEmpty()) return labels;

            Map<Integer, Product> productMap = productRepo.findAllById(productIds).stream()
                    .collect(Collectors.toMap(Product::getProductId, p -> p));

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
                    // --- FIX: НЕ ДАЕМ НИЖЕ ПЕРЕЗАТЕРЕТЬ ЭТИ ЛЕЙБЛЫ ПУСТОТОЙ ---
                    res.put("param1", "Длина");
                    res.put("param2", "Ширина");
                    res.put("param3", "Высота");
                }

                // --- FIX: пустые параметры скрываем ТОЛЬКО если до этого не выставили осмысленный label ---
                if (p1.isEmpty()) res.putIfAbsent("param1", "");
                if (p2.isEmpty()) res.putIfAbsent("param2", "");
                if (p3.isEmpty()) res.putIfAbsent("param3", "");

                if (cov4 < COVERAGE_THRESHOLD) res.putIfAbsent("param4", "");
                if (cov5 < COVERAGE_THRESHOLD) res.putIfAbsent("param5", "");

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
                return s.filter(Objects::nonNull).map(String::trim).filter(v -> !v.isEmpty()).collect(Collectors.toList());
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
