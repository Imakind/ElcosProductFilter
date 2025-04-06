package com.example.productfilter.repository;

import com.example.productfilter.model.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface ProductRepository extends JpaRepository<Product, Integer> {
    List<Product> findByBrand_BrandId(Integer brandId);
    Page<Product> findAllByProductIdIn(Collection<Integer> productIds, Pageable pageable);

}
