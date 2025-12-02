package com.example.productfilter.repository;

import com.example.productfilter.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.*;

public interface ProductRepository extends JpaRepository<Product, Integer> {

    List<Product> findByBrand_BrandId(Integer brandId);

    Page<Product> findAllByProductIdIn(Collection<Integer> productIds, Pageable pageable);

    boolean existsByArticleCode(String articleCode);

    Optional<Product> findByArticleCode(String articleCode);

    Optional<Product> findByArticleCodeAndSupplier_SupplierId(String articleCode, Integer supplierId);

    // ====== ДОБАВИТЬ ЭТИ МЕТОДЫ ======

    @Modifying
    @Query("""
        update Product p
        set p.price = p.price * :coef
        where p.brand.brandId = :brandId
          and p.price is not null
    """)
    void multiplyPricesByBrand(@Param("brandId") Integer brandId,
                               @Param("coef") BigDecimal coef);

    @Modifying
    @Query("""
        update Product p
        set p.price = p.price * :coef
        where p.supplier.supplierId = :supplierId
          and p.price is not null
    """)
    void multiplyPricesBySupplier(@Param("supplierId") Integer supplierId,
                                  @Param("coef") BigDecimal coef);
}
