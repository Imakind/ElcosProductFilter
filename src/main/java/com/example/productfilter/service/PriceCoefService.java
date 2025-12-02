// service/PriceCoefService.java
package com.example.productfilter.service;

import com.example.productfilter.dto.ApplyCoefRequest;
import com.example.productfilter.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PriceCoefService {

    private final ProductRepository productRepository;

    public PriceCoefService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public void applyCoef(ApplyCoefRequest req) {
        if ("brand".equals(req.getType())) {
            productRepository.multiplyPricesByBrand(req.getId(), req.getCoef());
        } else if ("supplier".equals(req.getType())) {
            productRepository.multiplyPricesBySupplier(req.getId(), req.getCoef());
        } else {
            throw new IllegalArgumentException("Unknown type: " + req.getType());
        }
    }
}
