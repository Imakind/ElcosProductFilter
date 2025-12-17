package com.example.productfilter.service;

import com.example.productfilter.dto.ProductFilterDTO;
import com.example.productfilter.model.Product;
import com.example.productfilter.model.ProductCategories;
import com.example.productfilter.model.ProductParameters;
import com.example.productfilter.repository.ProductCategoriesRepository;
import com.example.productfilter.repository.ProductParameterRepository;
import com.example.productfilter.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ProductFilterService {

    private final ProductRepository productRepo;
    private final ProductCategoriesRepository productCategoriesRepo;
    private final ProductParameterRepository parameterRepo;

    public ProductFilterService(
            ProductRepository productRepo,
            ProductCategoriesRepository productCategoriesRepo,
            ProductParameterRepository parameterRepo
    ) {
        this.productRepo = productRepo;
        this.productCategoriesRepo = productCategoriesRepo;
        this.parameterRepo = parameterRepo;
    }

    /** ЕДИНСТВЕННЫЙ метод расчёта productIds */
    public Set<Integer> resolveProductIds(ProductFilterDTO f) {

        Set<Integer> ids = productRepo.findAll().stream()
                .filter(p -> f.brandId() == null ||
                        p.getBrand().getBrandId().equals(f.brandId()))
                .map(Product::getProductId)
                .collect(Collectors.toSet());

        if (f.groupId() != null || f.subGroupId() != null) {
            List<ProductCategories> links = productCategoriesRepo.findAll();

            if (f.groupId() != null) {
                Set<Integer> g = links.stream()
                        .filter(pc -> f.groupId().equals(pc.getCategoryId()))
                        .map(ProductCategories::getProductId)
                        .collect(Collectors.toSet());
                ids.retainAll(g);
            }

            if (f.subGroupId() != null) {
                Set<Integer> sg = links.stream()
                        .filter(pc -> f.subGroupId().equals(pc.getCategoryId()))
                        .map(ProductCategories::getProductId)
                        .collect(Collectors.toSet());
                ids.retainAll(sg);
            }
        }

        if (hasAnyParam(f)) {
            List<ProductParameters> params =
                    parameterRepo.findByProduct_ProductIdIn(ids);

            params = params.stream()
                    .filter(p -> f.param1() == null || f.param1().equals(p.getParam1()))
                    .filter(p -> f.param2() == null || f.param2().equals(p.getParam2()))
                    .filter(p -> f.param3() == null || f.param3().equals(p.getParam3()))
                    .filter(p -> f.param4() == null || f.param4().equals(p.getParam4()))
                    .filter(p -> f.param5() == null || f.param5().equals(p.getParam5()))
                    .toList();

            ids = params.stream()
                    .map(p -> p.getProduct().getProductId())
                    .collect(Collectors.toSet());
        }

        if (f.keyword() != null && !f.keyword().isBlank()) {
            String kw = f.keyword().toLowerCase();
            ids = productRepo.findAllById(ids).stream()
                    .filter(p -> p.getName() != null &&
                            p.getName().toLowerCase().contains(kw))
                    .map(Product::getProductId)
                    .collect(Collectors.toSet());
        }

        return ids;
    }

    private boolean hasAnyParam(ProductFilterDTO f) {
        return Stream.of(
                f.param1(), f.param2(), f.param3(),
                f.param4(), f.param5()
        ).anyMatch(v -> v != null && !v.isBlank());
    }
}
