package com.example.productfilter.service;

import com.example.productfilter.dto.ProductFilterDTO;
import com.example.productfilter.model.Product;
import com.example.productfilter.model.ProductCategories;
import com.example.productfilter.model.ProductParameters;
import com.example.productfilter.repository.CategoryRepository;
import com.example.productfilter.repository.ProductCategoriesRepository;
import com.example.productfilter.repository.ProductParameterRepository;
import com.example.productfilter.repository.ProductRepository;
import com.example.productfilter.repository.spec.ProductSpecs;
import com.example.productfilter.util.search.SearchNormalizer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ProductFilterService {

    private final ProductRepository productRepo;
    private final ProductCategoriesRepository productCategoriesRepo;
    private final ProductParameterRepository parameterRepo;
    private final CategoryRepository categoryRepo;

    public ProductFilterService(
            ProductRepository productRepo,
            ProductCategoriesRepository productCategoriesRepo,
            ProductParameterRepository parameterRepo,
            CategoryRepository categoryRepo
    ) {
        this.productRepo = productRepo;
        this.productCategoriesRepo = productCategoriesRepo;
        this.parameterRepo = parameterRepo;
        this.categoryRepo = categoryRepo;
    }

    public Set<Integer> resolveProductIds(ProductFilterDTO f) {

        // 1) База: сразу ограничиваем по бренду, если выбран
        List<Product> base = (f.brandId() != null)
                ? productRepo.findByBrand_BrandId(f.brandId())
                : productRepo.findAll();

        Set<Integer> ids = base.stream()
                .map(Product::getProductId)
                .collect(Collectors.toSet());

        // 2) Группа/подгруппа: группа = parent + children
        if (f.groupId() != null || f.subGroupId() != null) {

            Set<Integer> categoryIds = new HashSet<>();

            if (f.subGroupId() != null) {
                categoryIds.add(f.subGroupId());
            }

            if (f.groupId() != null) {
                categoryIds.add(f.groupId());
                // подгруппы выбранной группы
                categoryRepo.findByParentCategoryIdOrderByNameAsc(f.groupId())
                        .forEach(c -> categoryIds.add(c.getCategoryId()));
            }

            final Set<Integer> idsSnapshot = new HashSet<>(ids);

            Set<Integer> byCats = productCategoriesRepo.findAll().stream()
                    .filter(pc -> idsSnapshot.contains(pc.getProductId()))
                    .filter(pc -> categoryIds.contains(pc.getCategoryId()))
                    .map(ProductCategories::getProductId)
                    .collect(Collectors.toSet());

            ids.retainAll(byCats);
        }

        // 3) Параметры
        if (hasAnyParam(f) && !ids.isEmpty()) {
            List<ProductParameters> params = parameterRepo.findByProduct_ProductIdIn(ids);

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

        // 4) Keyword (как было — по name)
        if (f.keyword() != null && !f.keyword().isBlank() && !ids.isEmpty()) {
            String kw = f.keyword().toLowerCase(Locale.ROOT);
            ids = productRepo.findAllById(ids).stream()
                    .filter(p -> p.getName() != null && p.getName().toLowerCase(Locale.ROOT).contains(kw))
                    .map(Product::getProductId)
                    .collect(Collectors.toSet());
        }

        return ids;
    }

    private boolean hasAnyParam(ProductFilterDTO f) {
        return Stream.of(f.param1(), f.param2(), f.param3(), f.param4(), f.param5())
                .anyMatch(v -> v != null && !v.isBlank());
    }
}
