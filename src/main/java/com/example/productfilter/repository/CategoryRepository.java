package com.example.productfilter.repository;

import com.example.productfilter.model.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;


public interface CategoryRepository extends JpaRepository<Category, Integer> {
    List<Category> findByParentCategoryIdIsNull();
    List<Category> findByParentCategoryId(Integer parentId);

    @Query("SELECT DISTINCT c FROM ProductCategories pc JOIN Category c ON pc.categoryId = c.categoryId WHERE pc.productId IN :productIds")
    List<Category> findByProducts(@Param("productIds") Collection<Integer> productIds);

    @Query("SELECT DISTINCT c FROM ProductCategories pc " +
            "JOIN Category c ON pc.categoryId = c.categoryId " +
            "WHERE pc.productId IN :productIds AND c.parentCategoryId IS NULL")
    List<Category> findParentCategoriesByProducts(@Param("productIds") Collection<Integer> productIds);

}
