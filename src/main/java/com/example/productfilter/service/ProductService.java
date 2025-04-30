package com.example.productfilter.service;

import com.example.productfilter.dto.ProductEditDto;
import com.example.productfilter.model.Product;

public interface ProductService {
    Product getById(Integer id);
    void updateProduct(ProductEditDto dto);
    void deleteProduct(Integer productId);

}
