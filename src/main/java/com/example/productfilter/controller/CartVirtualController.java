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

        // 1) создаём и сохраняем виртуальный товар
        Product vp = cartService.addVirtualProduct(req);
        Integer id = vp.getProductId(); // productId у тебя Integer

        // 2) корзина из сессии -> нормализуем в Map<Integer,Integer>
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

        // 3) !!! ВАЖНО: маппинг папок productId -> sectionId хранится в "productSection"
        @SuppressWarnings("unchecked")
        Map<Integer, Long> ps = (Map<Integer, Long>) session.getAttribute("productSection");
        if (ps == null) ps = new HashMap<>();

        ps.put(id, 1L); // кладём новый товар в "Общий" (id=1)
        session.setAttribute("productSection", ps);

        log.info("Cart after add: {}", cart);
        log.info("Section mapping after add: {}", ps);

        int cartCount = cart.values().stream().mapToInt(i -> i).sum();
        return Map.of("ok", true, "productId", id, "cartCount", cartCount);
    }


    // чтобы не было WARN "GET not supported", если кто-то дернёт ссылку
    @GetMapping("/cart/virtual/add")
    public String virtualAddGet() {
        return "redirect:/cart";
    }
}
