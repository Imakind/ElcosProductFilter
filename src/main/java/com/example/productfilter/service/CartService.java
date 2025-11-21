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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.productfilter.repository.ProductParameterRepository;
import com.example.productfilter.model.ProductParameters;


@Service
public class CartService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CartStore cartStore;
    private final ProductParameterRepository parameterRepo; // <-- ДОБАВИЛИ

    public CartService(ProductRepository productRepository,
                       BrandRepository brandRepository,
                       CartStore cartStore,
                       ProductParameterRepository parameterRepo) { // <-- ДОБАВИЛИ
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.cartStore = cartStore;
        this.parameterRepo = parameterRepo; // <-- ДОБАВИЛИ
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

        vp = productRepository.saveAndFlush(vp);  // <-- id появился

        saveVirtualParams(vp, req);              // <-- ВОТ ТУТ

        return vp;
    }


    private void saveVirtualParams(Product vp, VirtualProductRequest req) {
        if (req.getResults() == null) return;

        ProductParameters pp = new ProductParameters();
        pp.setProduct(vp);

        // 1) габариты
        pp.setParam1(String.format(
                "Габариты: В=%d мм, Ш=%d мм, Г=%d мм, t=%.1f мм",
                (int) req.getHeightMm(),
                (int) req.getWidthMm(),
                (int) req.getWidthMm(),
                req.getThicknessMm()
        ));

        // 2) количество частей
        pp.setParam2(String.format(
                "Части: перед/зад=%.1f шт, боковые=%.1f шт, верх/низ=%.1f шт",
                req.getFrontBackQty(),
                req.getSideQty(),
                req.getTopBottomQty()
        ));

        // 3) доп. элементы
        pp.setParam3(String.format(
                "Доп.: фальш-панель=%.1f шт, внутренности=%.1f шт",
                req.getFalsePanelQty(),
                req.getInnerQty()
        ));

        // 4) материалы/коэфы
        pp.setParam4(String.format(
                "Материалы: краска=%.2f кг/м² @%d тг, металл=%d тг/кг, Kсложн=%.2f",
                req.getPaintKgPerM2(),
                (int) req.getPaintPrice(),
                (int) req.getMetalPrice(),
                req.getComplexityK()
        ));

        // 5) результаты
        pp.setParam5(String.format(
                "Результат: S=%.2f м², масса=%.2f кг, себест=%d тг (%.2f €)",
                req.getResults().getAreaM2(),
                req.getResults().getMassKg(),
                req.getResults().getCostTenge(),
                req.getResults().getCostEuro()
        ));

        parameterRepo.saveAndFlush(pp);
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
