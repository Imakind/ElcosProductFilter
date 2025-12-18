package com.example.productfilter.repository;

import com.example.productfilter.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;
import java.util.Collection;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

    // Все корневые группы (без фильтра)
    List<Category> findByParentCategoryIdIsNull();

    // Подгруппы по parentId (без учёта товаров)
    List<Category> findByParentCategoryId(Integer parentId);

    // Все категории, в которых есть товары
    @Query("""
        SELECT DISTINCT c
        FROM ProductCategories pc
        JOIN Category c ON pc.categoryId = c.categoryId
        WHERE pc.productId IN :productIds
    """)
    List<Category> findByProducts(@Param("productIds") Collection<Integer> productIds);

    // ГРУППЫ по товарам (parent = null)
    @Query("""
        SELECT DISTINCT c
        FROM ProductCategories pc
        JOIN Category c ON pc.categoryId = c.categoryId
        WHERE pc.productId IN :productIds
          AND c.parentCategoryId IS NULL
    """)
    List<Category> findParentCategoriesByProducts(@Param("productIds") Collection<Integer> productIds);

    // ПОДГРУППЫ по товарам (parent != null)
    @Query("""
        SELECT DISTINCT c
        FROM ProductCategories pc
        JOIN Category c ON pc.categoryId = c.categoryId
        WHERE pc.productId IN :productIds
          AND c.parentCategoryId IS NOT NULL
    """)
    List<Category> findSubCategoriesByProducts(@Param("productIds") Collection<Integer> productIds);

    // Все подгруппы (без фильтра)
    @Query("SELECT c FROM Category c WHERE c.parentCategoryId IS NOT NULL")
    List<Category> findAllSubGroups();

    // Все подгруппы (без фильтра) + сортировка по имени
    @Query("SELECT c FROM Category c WHERE c.parentCategoryId IS NOT NULL ORDER BY c.name ASC")
    List<Category> findAllSubGroupsOrderByNameAsc();

    Optional<Category> findByNameIgnoreCase(String name);

    // Подгруппы по parentId + сортировка (через parentCategoryId, без поля parentCategory)
    List<Category> findByParentCategoryIdOrderByNameAsc(Integer parentId);

    // Все подгруппы (parentCategoryId != null) + сортировка (исправление твоего падающего метода)
    List<Category> findByParentCategoryIdIsNotNullOrderByNameAsc();
}
