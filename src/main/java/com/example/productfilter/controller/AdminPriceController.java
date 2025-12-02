// controller/AdminPriceController.java
package com.example.productfilter.controller;

import com.example.productfilter.dto.ApplyCoefRequest;
import com.example.productfilter.service.PriceCoefService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/api/prices")
public class AdminPriceController {

    private final PriceCoefService priceCoefService;

    public AdminPriceController(PriceCoefService priceCoefService) {
        this.priceCoefService = priceCoefService;
    }

    @PostMapping("/apply-coef")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> applyCoef(@RequestBody ApplyCoefRequest req) {
        priceCoefService.applyCoef(req);
        return ResponseEntity.ok().build();
    }
}
