// service/CartService.java (фрагмент)
package com.example.productfilter.service;

import com.example.productfilter.dto.VirtualProductRequest;
import com.example.productfilter.model.Brand;
import com.example.productfilter.model.Product;
import com.example.productfilter.repository.BrandRepository;
import com.example.productfilter.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class CartService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CartStore cartStore; // твой класс корзины/сессии

    public CartService(ProductRepository productRepository, BrandRepository brandRepository, CartStore cartStore) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.cartStore = cartStore;
    }

    @Transactional
    public Product addVirtualProduct(VirtualProductRequest req) {

        Product vp = new Product();
        vp.setName(buildVirtualName(req));
        vp.setArticleCode("VIRT-" + UUID.randomUUID());
        vp.setPrice(BigDecimal.valueOf(req.getResults().getCostTenge()));

        Brand elcos = brandRepository.findByBrandNameIgnoreCase("Elcos")
                .orElseGet(() -> brandRepository.save(new Brand("Elcos")));
        vp.setBrand(elcos);

        // если есть флаг виртуальности — поставь
        // vp.setVirtual(true);

        return productRepository.saveAndFlush(vp);
    }



    private String buildVirtualName(VirtualProductRequest req) {
        return String.format(
                "Шкаф %sx%sx%s мм, t=%s мм",
                (int)req.getHeightMm(),
                (int)req.getWidthMm(),
                (int)req.getDepthMm(),
                req.getThicknessMm()
        );
    }
}
