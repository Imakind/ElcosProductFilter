package com.example.productfilter.repository;

import com.example.productfilter.model.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SupplierRepository extends JpaRepository<Supplier, Integer> {
    Optional<Supplier> findByNameIgnoreCase(String name);
}
