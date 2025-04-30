package com.example.productfilter.controller;

import com.example.productfilter.dto.ProductEditDto;
import com.example.productfilter.model.Product;
import com.example.productfilter.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
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

    @PostMapping("/edit-product")
    public void editProduct(@ModelAttribute ProductEditDto dto) {
        productService.updateProduct(dto);
    }

    @DeleteMapping("/product/{id}")
    public void deleteProduct(@PathVariable("id") Integer id) {
        productService.deleteProduct(id);
    }

}
