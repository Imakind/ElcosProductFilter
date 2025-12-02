package com.example.productfilter.controller.admin;

import com.example.productfilter.model.Supplier;
import com.example.productfilter.repository.SupplierRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/api")
public class SupplierApiController {

    private final SupplierRepository supplierRepository;

    public SupplierApiController(SupplierRepository supplierRepository) {
        this.supplierRepository = supplierRepository;
    }

    @GetMapping("/suppliers")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Supplier> getSuppliers() {
        return supplierRepository.findAll();
    }
}
