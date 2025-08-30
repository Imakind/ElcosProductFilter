package com.example.productfilter.controller;

import com.example.productfilter.dto.ProductEditDto;
import com.example.productfilter.model.Product;
import com.example.productfilter.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/api")
public class AdminProductApiController {

    @Autowired
    private ProductService productService;

    @GetMapping("/product")
    public Product getProduct(@RequestParam("id") Integer id) {
        return productService.getById(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/edit-product")
    public void editProduct(@ModelAttribute ProductEditDto dto) {
        productService.updateProduct(dto);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/product/{id}")
    public void deleteProduct(@PathVariable("id") Integer id) {
        productService.deleteProduct(id);
    }

}
