package com.example.productfilter.controller;

import com.example.productfilter.model.Category;
import com.example.productfilter.model.Product;
import com.example.productfilter.model.ProductParameters;
import com.example.productfilter.repository.BrandRepository;
import com.example.productfilter.repository.CategoryRepository;
import com.example.productfilter.repository.ProductParameterRepository;
import com.example.productfilter.repository.ProductRepository;
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

    @GetMapping("/filter/subgroups")
    @ResponseBody
    public List<Category> getSubGroups(@RequestParam("groupId") Integer groupId) {
        return categoryRepo.findByParentCategoryId(groupId);
    }


}
