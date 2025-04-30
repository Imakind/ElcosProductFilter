package com.example.productfilter.repository;

import com.example.productfilter.model.ProductCategories;
import com.example.productfilter.model.ProductCategoriesId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ProductCategoriesRepository extends JpaRepository<ProductCategories, ProductCategoriesId> {

    @Query("SELECT pc.categoryId FROM ProductCategories pc WHERE pc.productId IN :productIds")
    List<Integer> findCategoryIdsByProductIds(@Param("productIds") Collection<Integer> productIds);

    List<ProductCategories> findByProductIdIn(Collection<Integer> productIds);

    void deleteByProductId(Integer productId);

}
