package com.example.productfilter.specification;

import com.example.productfilter.model.Product;
import com.example.productfilter.model.ProductCategories;
import com.example.productfilter.model.ProductParameters;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.Set;

public class ProductSpecifications {

    public static Specification<Product> filterBy(
            Integer brandId,
            Set<Integer> categoryProductIds,
            Set<Integer> parameterProductIds
    ) {
        return (Root<Product> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            Predicate predicate = cb.conjunction();

            if (brandId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("brand").get("brandId"), brandId));
            }

            if (categoryProductIds != null && !categoryProductIds.isEmpty()) {
                predicate = cb.and(predicate, root.get("productId").in(categoryProductIds));
            }

            if (parameterProductIds != null && !parameterProductIds.isEmpty()) {
                predicate = cb.and(predicate, root.get("productId").in(parameterProductIds));
            }

            return predicate;
        };
    }
}
