package com.example.productfilter.service.impl;

import com.example.productfilter.model.Brand;
import com.example.productfilter.repository.BrandRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BrandService {
    @Autowired
    private BrandRepository brandRepository;

    public Brand getOrCreateBrand(String brandName) {
        return brandRepository.findByBrandNameIgnoreCase(brandName)
                .orElseGet(() -> {
                    Brand newBrand = new Brand(brandName);
                    return brandRepository.save(newBrand);
                });
    }

}
