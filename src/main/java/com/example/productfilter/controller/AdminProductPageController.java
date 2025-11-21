package com.example.productfilter.controller;

import com.example.productfilter.repository.BrandRepository;
import com.example.productfilter.repository.CategoryRepository;
import com.example.productfilter.repository.ProductParameterRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminProductPageController {

    @Autowired
    private BrandRepository brandRepo;
    @Autowired private CategoryRepository categoryRepo;
    @Autowired private ProductParameterRepository parameterRepo;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/add-product")
    public String addProductPage(Model model, HttpSession session) {
        model.addAttribute("brands", brandRepo.findAll());
        model.addAttribute("groups", categoryRepo.findByParentCategoryIdIsNull());
        model.addAttribute("param1List", parameterRepo.findDistinctParam1());
        model.addAttribute("filterParams", session.getAttribute("lastFilters"));
        return "add_product";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/add-product")
    public String addProduct(/* dto */) {
        // save product
        return "redirect:/";
    }
}
