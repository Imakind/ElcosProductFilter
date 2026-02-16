package com.example.productfilter.controller;

import com.example.productfilter.model.Product;
import com.example.productfilter.repository.ProductRepository;
import com.example.productfilter.service.CartService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
public class CartExtraController {

    private final CartService cartService;
    private final ProductRepository productRepo;

    public CartExtraController(CartService cartService, ProductRepository productRepo) {
        this.cartService = cartService;
        this.productRepo = productRepo;
    }

    @PostMapping("/cart/smr/add")
    public Map<String, Object> addSMR(@RequestParam String name,
                                      @RequestParam double price,
                                      @RequestParam(required = false) Long sectionId,
                                      HttpSession session) {
        Product p = cartService.addSMRProduct(name, price);
        Integer id = p.getProductId();

        // 1. Add to cart quantity
        @SuppressWarnings("unchecked")
        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("cart");
        if (cart == null) cart = new HashMap<>();
        cart.merge(id, 1, Integer::sum);
        session.setAttribute("cart", cart);

        // 2. Set price override
        @SuppressWarnings("unchecked")
        Map<Integer, Double> baseOverride = (Map<Integer, Double>) session.getAttribute("priceOverrideBaseMap");
        if (baseOverride == null) baseOverride = new HashMap<>();
        baseOverride.put(id, price);
        session.setAttribute("priceOverrideBaseMap", baseOverride);

        // 3. Assign section if provided
        if (sectionId != null) {
            @SuppressWarnings("unchecked")
            Map<Integer, Long> ps = (Map<Integer, Long>) session.getAttribute("productSection");
            if (ps == null) ps = new HashMap<>();
            ps.put(id, sectionId);
            session.setAttribute("productSection", ps);
        }

        return Map.of("ok", true, "productId", id);
    }

    @PostMapping("/cart/price/update")
    public Map<String, Object> updatePrice(@RequestParam Integer productId,
                                           @RequestParam double newPrice,
                                           HttpSession session) {
        // Validation: Allow only Virtual or SMR products
        Product p = productRepo.findById(productId).orElse(null);
        if (p == null) return Map.of("ok", false, "msg", "Товар не найден");

        boolean isVirtual = p.getArticleCode() != null && 
                            (p.getArticleCode().startsWith("VIRT-") || p.getArticleCode().startsWith("SMR-"));

        if (!isVirtual) {
            return Map.of("ok", false, "msg", "Цену этого товара нельзя изменить");
        }

        @SuppressWarnings("unchecked")
        Map<Integer, Double> baseOverride = (Map<Integer, Double>) session.getAttribute("priceOverrideBaseMap");
        if (baseOverride == null) baseOverride = new HashMap<>();
        baseOverride.put(productId, newPrice);
        session.setAttribute("priceOverrideBaseMap", baseOverride);

        // Calculate return values for UI update
        @SuppressWarnings("unchecked")
        Map<Integer, Integer> cart = (Map<Integer, Integer>) session.getAttribute("cart");
        int qty = cart != null ? cart.getOrDefault(productId, 1) : 1;
        
        @SuppressWarnings("unchecked")
        Map<Integer, Double> coeffMap = (Map<Integer, Double>) session.getAttribute("coefficientMap");
        double coeff = coeffMap != null ? coeffMap.getOrDefault(productId, 1.0) : 1.0;

        double finalUnit = newPrice * coeff;
        double lineSum = finalUnit * qty;

        // Update total sum logic implies full recalculation on frontend or backend return total
        // Here we just return line info, frontend usually handles total sum or reloads
        
        return Map.of(
            "ok", true, 
            "productId", productId,
            "newBasePrice", newPrice,
            "unitPrice", finalUnit, // with coeff
            "lineSum", lineSum
        );
    }
}
