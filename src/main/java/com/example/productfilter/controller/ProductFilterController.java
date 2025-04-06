package com.example.productfilter.controller;

import com.example.productfilter.model.Category;
import com.example.productfilter.model.Product;
import com.example.productfilter.model.ProductCategories;
import com.example.productfilter.model.ProductParameters;
import com.example.productfilter.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.*;
import java.util.stream.Collectors;


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
    public String index(Model model) {
        // 🟢 1. Загрузить бренды
        model.addAttribute("brands", brandRepo.findAll());

        // 🟢 2. Загрузить родительские категории (группы)
        model.addAttribute("groups", categoryRepo.findByParentCategoryIdIsNull());

        // 🟢 3. Загрузить подкатегории — временно любую (например, с parentId = 1)
        model.addAttribute("subGroups", categoryRepo.findByParentCategoryId(1));

        // 🟢 4. Загрузить списки параметров (уникальные значения)
        model.addAttribute("param1List", parameterRepo.findDistinctParam1());
        model.addAttribute("param2List", parameterRepo.findDistinctParam2());
        model.addAttribute("param3List", parameterRepo.findDistinctParam3());
        model.addAttribute("param4List", parameterRepo.findDistinctParam4());
        model.addAttribute("param5List", parameterRepo.findDistinctParam5());

        return "filter";
    }

    @GetMapping("/filter/options")
    @ResponseBody
    public Map<String, Object> getOptionsByBrand(@RequestParam("brandId") Integer brandId) {
        Map<String, Object> response = new HashMap<>();

        // Найдём продукты этого бренда
        List<Product> products = productRepo.findByBrand_BrandId(brandId);

        // Категории этих продуктов
        Set<Integer> productIds = products.stream().map(Product::getProductId).collect(Collectors.toSet());
        List<Category> allCategories = categoryRepo.findByProducts(productIds);

// ❗ фильтруем только родительские категории
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
            Model model) {

        // 🟢 ВСЕ фильтры для повторного отображения полей
        model.addAttribute("brands", brandRepo.findAll());
        model.addAttribute("groups", categoryRepo.findByParentCategoryIdIsNull());
        model.addAttribute("subGroups", groupId != null ? categoryRepo.findByParentCategoryId(groupId) : List.of());
        model.addAttribute("param1List", parameterRepo.findDistinctParam1());
        model.addAttribute("param2List", parameterRepo.findDistinctParam2());
        model.addAttribute("param3List", parameterRepo.findDistinctParam3());
        model.addAttribute("param4List", parameterRepo.findDistinctParam4());
        model.addAttribute("param5List", parameterRepo.findDistinctParam5());

        // 🟢 Повторим значения выбранных параметров, чтобы Thymeleaf отобразил selected
        model.addAttribute("param", Map.of(
                "brandId", brandId,
                "groupId", groupId,
                "subGroupId", subGroupId,
                "param1", param1,
                "param2", param2,
                "param3", param3,
                "param4", param4,
                "param5", param5
        ));

        // 🟢 Получаем продукты
        List<Product> products = productRepo.findAll();
        if (brandId != null) {
            products = products.stream()
                    .filter(p -> p.getBrand().getBrandId().equals(brandId))
                    .collect(Collectors.toList());
        }

        Set<Integer> productIds = products.stream().map(Product::getProductId).collect(Collectors.toSet());

        // 🟢 Категории
        if (groupId != null || subGroupId != null) {
            List<ProductCategories> links = productCategoriesRepo.findAll();

            if (groupId != null) {
                Set<Integer> byGroup = links.stream()
                        .filter(pc -> pc.getCategoryId().equals(groupId))
                        .map(ProductCategories::getProductId)
                        .collect(Collectors.toSet());
                productIds.retainAll(byGroup);
            }

            if (subGroupId != null) {
                Set<Integer> bySub = links.stream()
                        .filter(pc -> pc.getCategoryId().equals(subGroupId))
                        .map(ProductCategories::getProductId)
                        .collect(Collectors.toSet());
                productIds.retainAll(bySub);
            }
        }

        // 🟢 Параметры
        List<ProductParameters> params = parameterRepo.findByProduct_ProductIdIn(productIds);

        if (param1 != null && !param1.isEmpty())
            params = params.stream().filter(p -> param1.equals(p.getParam1())).collect(Collectors.toList());
        if (param2 != null && !param2.isEmpty())
            params = params.stream().filter(p -> param2.equals(p.getParam2())).collect(Collectors.toList());
        if (param3 != null && !param3.isEmpty())
            params = params.stream().filter(p -> param3.equals(p.getParam3())).collect(Collectors.toList());
        if (param4 != null && !param4.isEmpty())
            params = params.stream().filter(p -> param4.equals(p.getParam4())).collect(Collectors.toList());
        if (param5 != null && !param5.isEmpty())
            params = params.stream().filter(p -> param5.equals(p.getParam5())).collect(Collectors.toList());

        Set<Integer> filteredProductIds = params.stream()
                .map(p -> p.getProduct().getProductId())
                .collect(Collectors.toSet());

        List<Product> filteredProducts = productRepo.findAllById(filteredProductIds);

        model.addAttribute("products", filteredProducts);

        return "filter";
    }


}
