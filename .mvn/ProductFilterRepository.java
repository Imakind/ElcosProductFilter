package com.example.productfilter.repository;

import com.example.productfilter.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductFilterRepository extends JpaRepository<Product, Long> {

    // 1) Список брендов
    @Query(value = """
        SELECT DISTINCT b.brand_name
        FROM products p
        JOIN brands b ON p.brand_id = b.brand_id
        """, nativeQuery = true)
    List<String> findDistinctBrands();

    // 2) Список групп (категорий верхнего уровня) по выбранному бренду
    //    Предполагаем, что parent_category_id IS NULL => это "группа"
    @Query(value = """
        SELECT DISTINCT c.name
        FROM products p
        JOIN brands b ON p.brand_id = b.brand_id
        JOIN product_categories pc ON pc.product_id = p.product_id
        JOIN categories c ON c.category_id = pc.category_id
        WHERE b.brand_name = :brand
          AND c.parent_category_id IS NULL
        """, nativeQuery = true)
    List<String> findDistinctGroupsByBrand(@Param("brand") String brand);

    // 3) Список подгрупп по выбранному бренду и выбранной группе
    //    Подгруппа — это категория, у которой parent_category_id = category_id группы
    @Query(value = """
        SELECT DISTINCT c2.name
        FROM products p
        JOIN brands b ON p.brand_id = b.brand_id
        JOIN product_categories pc ON pc.product_id = p.product_id
        JOIN categories c2 ON c2.category_id = pc.category_id
        JOIN categories c1 ON c2.parent_category_id = c1.category_id
        WHERE b.brand_name = :brand
          AND c1.name = :groupName
        """, nativeQuery = true)
    List<String> findDistinctSubGroups(@Param("brand") String brand,
                                       @Param("groupName") String groupName);

    // 4) Список param1 по выбранным бренду, группе, подгруппе
    @Query(value = """
        SELECT DISTINCT pp.param1
        FROM products p
        JOIN brands b ON p.brand_id = b.brand_id
        JOIN product_categories pc ON pc.product_id = p.product_id
        JOIN categories c2 ON c2.category_id = pc.category_id
        JOIN categories c1 ON c2.parent_category_id = c1.category_id
        JOIN product_parameters pp ON pp.product_id = p.product_id
        WHERE b.brand_name = :brand
          AND c1.name = :groupName
          AND c2.name = :subGroupName
        """, nativeQuery = true)
    List<String> findDistinctParam1(@Param("brand") String brand,
                                    @Param("groupName") String groupName,
                                    @Param("subGroupName") String subGroupName);

    // 5) Список param2 по brand, group, subgroup, param1
    @Query(value = """
        SELECT DISTINCT pp.param2
        FROM products p
        JOIN brands b ON p.brand_id = b.brand_id
        JOIN product_categories pc ON pc.product_id = p.product_id
        JOIN categories c2 ON c2.category_id = pc.category_id
        JOIN categories c1 ON c2.parent_category_id = c1.category_id
        JOIN product_parameters pp ON pp.product_id = p.product_id
        WHERE b.brand_name = :brand
          AND c1.name = :groupName
          AND c2.name = :subGroupName
          AND pp.param1 = :param1
        """, nativeQuery = true)
    List<String> findDistinctParam2(@Param("brand") String brand,
                                    @Param("groupName") String groupName,
                                    @Param("subGroupName") String subGroupName,
                                    @Param("param1") String param1);

    // Аналогично для param3, param4, param5...
    // При необходимости добавьте их сами по аналогии
}
