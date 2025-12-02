package com.example.productfilter.service.impl;

import com.example.productfilter.model.Brand;
import com.example.productfilter.repository.BrandRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BrandService {

    private final BrandRepository brandRepository;

    public BrandService(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
    }

    public synchronized Brand getOrCreateBrand(String brandName) {

        return brandRepository.findByBrandNameIgnoreCase(brandName)
                .orElseGet(() -> {
                    Brand b = new Brand(brandName);
                    return brandRepository.save(b);
                });
    }
}
