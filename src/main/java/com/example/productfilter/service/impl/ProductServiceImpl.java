package com.example.productfilter.service.impl;

import com.example.productfilter.dto.ProductEditDto;
import com.example.productfilter.model.Product;
import com.example.productfilter.repository.ProductCategoriesRepository;
import com.example.productfilter.repository.ProductParameterRepository;
import com.example.productfilter.repository.ProductRepository;
import com.example.productfilter.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductServiceImpl implements ProductService {

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private ProductParameterRepository parameterRepo;
    @Autowired
    private ProductCategoriesRepository productCategoriesRepo;

    @Override
    public Product getById(Integer id) {
        return productRepo.findById(id).orElseThrow();
    }

    @Override
    public void updateProduct(ProductEditDto dto) {
        Product product = productRepo.findById(dto.getProductId()).orElseThrow();
        product.setName(dto.getName());
        product.setPrice(dto.getPrice());
        product.setArticleCode(dto.getArticleCode());
        productRepo.save(product);
    }

    @Transactional
    @Override
    public void deleteProduct(Integer productId) {
        // Удалить параметры
        parameterRepo.deleteByProduct_ProductId(productId);

        // Удалить связи с категориями
        productCategoriesRepo.deleteByProductId(productId);

        // Удалить сам товар
        productRepo.deleteById(productId);
    }
}
