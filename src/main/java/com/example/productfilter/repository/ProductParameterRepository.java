package com.example.productfilter.repository;

import com.example.productfilter.model.ProductParameters;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface ProductParameterRepository extends JpaRepository<ProductParameters, Integer> {

    @Query("SELECT DISTINCT p.param1 FROM ProductParameters p WHERE p.param1 IS NOT NULL")
    List<String> findDistinctParam1();

    @Query("SELECT DISTINCT p.param2 FROM ProductParameters p WHERE p.param2 IS NOT NULL")
    List<String> findDistinctParam2();

    @Query("SELECT DISTINCT p.param3 FROM ProductParameters p WHERE p.param3 IS NOT NULL")
    List<String> findDistinctParam3();

    @Query("SELECT DISTINCT p.param4 FROM ProductParameters p WHERE p.param4 IS NOT NULL")
    List<String> findDistinctParam4();

    @Query("SELECT DISTINCT p.param5 FROM ProductParameters p WHERE p.param5 IS NOT NULL")
    List<String> findDistinctParam5();

    List<ProductParameters> findByProduct_ProductIdIn(Collection<Integer> productIds);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional // можно убрать, т.к. вызываете из @Transactional сервиса
    int deleteByProduct_ProductId(Integer productId);
}
