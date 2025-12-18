package com.example.productfilter.repository;

import com.example.productfilter.model.Category;
import com.example.productfilter.model.ProductCategories;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Integer> {

    // Все корневые группы
    List<Category> findByParentCategoryIdIsNull();

    // Подгруппы по parentId (отсортировано)
    List<Category> findByParentCategoryIdOrderByNameAsc(Integer parentId);

    // Все подгруппы (отсортировано)
    @Query("SELECT c FROM Category c WHERE c.parentCategoryId IS NOT NULL ORDER BY c.name ASC")
    List<Category> findAllSubGroupsOrderByNameAsc();

    // Получить parentCategoryId по categoryId (нужно для subGroupId -> groupId)
    @Query("SELECT c.parentCategoryId FROM Category c WHERE c.categoryId = :categoryId")
    Integer findParentIdByCategoryId(@Param("categoryId") Integer categoryId);

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

    Optional<Category> findByNameIgnoreCase(String name);
}
