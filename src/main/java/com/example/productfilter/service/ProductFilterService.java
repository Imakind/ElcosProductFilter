package com.example.productfilter.service;

import com.example.productfilter.dto.ProductFilterDTO;
import com.example.productfilter.model.Product;
import com.example.productfilter.repository.ProductCategoriesRepository;
import com.example.productfilter.repository.ProductParameterRepository;
import com.example.productfilter.repository.ProductRepository;
import com.example.productfilter.repository.spec.ProductSpecs;
import com.example.productfilter.util.search.SearchNormalizer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ProductFilterService {

    private final ProductRepository productRepo;
    private final ProductCategoriesRepository productCategoriesRepo;
    private final ProductParameterRepository parameterRepo;
    private final SearchNormalizer normalizer;

    public ProductFilterService(
            ProductRepository productRepo,
            ProductCategoriesRepository productCategoriesRepo,
            ProductParameterRepository parameterRepo,
            SearchNormalizer normalizer
    ) {
        this.productRepo = productRepo;
        this.productCategoriesRepo = productCategoriesRepo;
        this.parameterRepo = parameterRepo;
        this.normalizer = normalizer;
    }

    /** ТВОЙ ТЕКУЩИЙ resolveProductIds — оставляем (ниже можно улучшать производительность) */
    public Set<Integer> resolveProductIds(ProductFilterDTO f) {
        Set<Integer> ids = productRepo.findAll().stream()
                .filter(p -> f.brandId() == null || p.getBrand().getBrandId().equals(f.brandId()))
                .map(Product::getProductId)
                .collect(Collectors.toSet());

        // ... (твой код по категориям/параметрам/keyword как был)
        // ВАЖНО: keyword в resolveProductIds лучше НЕ использовать для Page-поиска (см. search ниже)

        if (f.keyword() != null && !f.keyword().isBlank()) {
            String kw = f.keyword().toLowerCase();
            ids = productRepo.findAllById(ids).stream()
                    .filter(p -> p.getName() != null && p.getName().toLowerCase().contains(kw))
                    .map(Product::getProductId)
                    .collect(Collectors.toSet());
        }

        return ids;
    }

    private boolean hasAnyParam(ProductFilterDTO f) {
        return Stream.of(f.param1(), f.param2(), f.param3(), f.param4(), f.param5())
                .anyMatch(v -> v != null && !v.isBlank());
    }

    /** Пэйджинг + “эластичный” keyword (имя/артикул/дата) — БЕЗ SQL-миграций */
    public Page<Product> search(ProductFilterDTO f, Pageable pageable) {

        // 1) ids считаем по фильтрам, но keyword убираем, чтобы keywordElastic работал “шире”
        ProductFilterDTO ctxNoKeyword = new ProductFilterDTO(
                f.brandId(), f.groupId(), f.subGroupId(),
                f.param1(), f.param2(), f.param3(), f.param4(), f.param5(),
                null
        );

        Set<Integer> ids = resolveProductIds(ctxNoKeyword);

        // 2) keywordElastic уже применяем в спецификации (имя + артикул + дата)
        Specification<Product> spec =
                ProductSpecs.idIn(ids)
                        .and(ProductSpecs.keywordElastic(f.keyword(), normalizer));

        return productRepo.findAll(spec, pageable);
    }
}
