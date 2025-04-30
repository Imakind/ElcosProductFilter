package com.example.productfilter.repository;

import com.example.productfilter.model.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;

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

    void deleteByProduct_ProductId(Integer productId);

}