package com.example.productfilter.controller;

import com.example.productfilter.dto.VirtualProductRequest;
import com.example.productfilter.model.Product;
import com.example.productfilter.service.CartService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
public class CartVirtualController {

    private static final Logger log = LoggerFactory.getLogger(CartVirtualController.class);
    private final CartService cartService;

    public CartVirtualController(CartService cartService) {
        this.cartService = cartService;
    }

    @PostMapping("/cart/virtual/add")
    public Map<String, Object> addVirtualToCart(@RequestBody VirtualProductRequest req,
                                                HttpSession session) {
        log.info("Virtual req: {}", req);

        Product vp = cartService.addVirtualProduct(req);
        Integer id = vp.getProductId();

        // --- qty ---
        @SuppressWarnings("unchecked")
        Map<?, Integer> cartRaw = (Map<?, Integer>) session.getAttribute("cart");
        if (cartRaw == null) cartRaw = new HashMap<>();

        Map<Integer, Integer> cart = new HashMap<>();
        for (Map.Entry<?, Integer> e : cartRaw.entrySet()) {
            Object k = e.getKey();
            Integer pid = (k instanceof Number n) ? n.intValue() : Integer.valueOf(k.toString());
            cart.put(pid, e.getValue());
        }
        cart.merge(id, 1, Integer::sum);
        session.setAttribute("cart", cart);

        // --- section mapping ---
        @SuppressWarnings("unchecked")
        Map<Integer, Long> ps = (Map<Integer, Long>) session.getAttribute("productSection");
        if (ps == null) ps = new HashMap<>();
        ps.put(id, 1L);
        session.setAttribute("productSection", ps);

        // --- IMPORTANT: unit price (C87) ---
        int unitPrice =
                req.getUnitPriceTenge() > 0 ? req.getUnitPriceTenge()
                        : (req.getResults() != null && req.getResults().getFinalPriceTenge() > 0) ? req.getResults().getFinalPriceTenge()
                        : (req.getResults() != null ? req.getResults().getCostTenge() : 0);

// ✅ Храним ТОЛЬКО базовую цену
        @SuppressWarnings("unchecked")
        Map<Integer, Double> baseOverride =
                (Map<Integer, Double>) session.getAttribute("priceOverrideBaseMap");
        if (baseOverride == null) baseOverride = new HashMap<>();
        baseOverride.put(id, (double) unitPrice);
        session.setAttribute("priceOverrideBaseMap", baseOverride);

// ✅ Никаких unitPriceMap/priceOverrideMap как “источника правды”
        session.removeAttribute("priceOverrideMap");
        session.removeAttribute("unitPriceMap");
        session.removeAttribute("lineSumMap");


        @SuppressWarnings("unchecked")
        Map<Integer, Double> unitPriceSession = (Map<Integer, Double>) session.getAttribute("unitPriceMap");
        if (unitPriceSession == null) unitPriceSession = new HashMap<>();
        unitPriceSession.put(id, (double) unitPrice);
        session.setAttribute("unitPriceMap", unitPriceSession);

        int cartCount = cart.values().stream().mapToInt(i -> i).sum();
        return Map.of("ok", true, "productId", id, "cartCount", cartCount, "unitPrice", unitPrice);
    }



    // чтобы не было WARN "GET not supported", если кто-то дернёт ссылку
    @GetMapping("/cart/virtual/add")
    public String virtualAddGet() {
        return "redirect:/cart";
    }
}
