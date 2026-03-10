package com.example.productfilter.controller;

import com.example.productfilter.model.Product;
import com.example.productfilter.repository.ProductRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api/cart")
public class CartApiController {

    private final ProductRepository productRepo;

    public CartApiController(ProductRepository productRepo) {
        this.productRepo = productRepo;
    }

    @GetMapping("/details")
    public Map<String, Object> getCartDetails(HttpSession session) {
        // --- 1. Quantities ---
        @SuppressWarnings("unchecked")
        Map<?, Integer> cartRaw = (Map<?, Integer>) session.getAttribute("cart");
        if (cartRaw == null) cartRaw = new HashMap<>();
        Map<Integer, Integer> cart = new HashMap<>();
        for (Map.Entry<?, Integer> e : cartRaw.entrySet()) {
            Object key = e.getKey();
            Integer pid = (key instanceof Number) ? ((Number) key).intValue() : Integer.valueOf(key.toString());
            cart.put(pid, e.getValue());
        }

        // --- 2. Coefficients ---
        @SuppressWarnings("unchecked")
        Map<?, Double> coeffRaw = (Map<?, Double>) session.getAttribute("coefficientMap");
        if (coeffRaw == null) coeffRaw = new HashMap<>();
        Map<Integer, Double> coefficientMap = new HashMap<>();
        for (Map.Entry<?, Double> e : coeffRaw.entrySet()) {
            Object key = e.getKey();
            Object value = e.getValue();
            Integer pid = (key instanceof Number) ? ((Number) key).intValue() : Integer.valueOf(key.toString());
            Double val = (value instanceof Number) ? ((Number) value).doubleValue() : Double.valueOf(value.toString());
            coefficientMap.put(pid, val);
        }

        // --- 3. Folders Mapping ---
        @SuppressWarnings("unchecked")
        Map<Integer, Long> productSection = (Map<Integer, Long>) session.getAttribute("productSection");
        if (productSection == null) productSection = new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<Integer, Map<Long, Integer>> productSectionQty = (Map<Integer, Map<Long, Integer>>) session.getAttribute("productSectionQty");
        if (productSectionQty == null) productSectionQty = new HashMap<>();

        // --- 4. Base Overrides ---
        @SuppressWarnings("unchecked")
        Map<Integer, Double> baseOverrideRaw = (Map<Integer, Double>) session.getAttribute("priceOverrideBaseMap");
        if (baseOverrideRaw == null) baseOverrideRaw = new HashMap<>();
        Map<Integer, Double> baseOverride = new HashMap<>();
        for (Map.Entry<?, Double> e : baseOverrideRaw.entrySet()) {
            Object key = e.getKey();
            Object value = e.getValue();
            Integer pid = (key instanceof Number) ? ((Number) key).intValue() : Integer.valueOf(key.toString());
            Double val = (value instanceof Number) ? ((Number) value).doubleValue() : Double.valueOf(value.toString());
            baseOverride.put(pid, val);
        }

        List<Product> products = !cart.isEmpty() ? productRepo.findAllById(cart.keySet()) : List.of();

        List<Map<String, Object>> items = new ArrayList<>();
        for (Product p : products) {
            Integer pid = p.getProductId();
            int totalQty = cart.getOrDefault(pid, 0);
            if (totalQty <= 0) continue;

            double coeff = coefficientMap.getOrDefault(pid, 1.0);
            double basePrice = baseOverride.containsKey(pid) ? baseOverride.get(pid) : (p.getPrice() != null ? p.getPrice().doubleValue() : 0.0);

            Map<Long, Integer> splits = productSectionQty.get(pid);
            if (splits != null && !splits.isEmpty()) {
                // Если есть разделение по папкам
                for (Map.Entry<Long, Integer> entry : splits.entrySet()) {
                    Long sid = entry.getKey();
                    int sqty = entry.getValue();
                    if (sqty <= 0) continue;

                    items.add(createItemMap(p, pid, sqty, coeff, basePrice, sid));
                }
                
                // Проверяем, осталось ли нераспределенное количество
                int assigned = splits.values().stream().mapToInt(Integer::intValue).sum();
                if (totalQty > assigned) {
                    Long defSid = productSection.getOrDefault(pid, 1L);
                    items.add(createItemMap(p, pid, totalQty - assigned, coeff, basePrice, defSid));
                }
            } else {
                // Обычный товар в одной папке
                Long sectionId = productSection.getOrDefault(pid, 1L);
                items.add(createItemMap(p, pid, totalQty, coeff, basePrice, sectionId));
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("items", items);
        return response;
    }

    private Map<String, Object> createItemMap(Product p, Integer pid, int qty, double coeff, double basePrice, Long sectionId) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", pid + "_" + sectionId); 
        item.put("productId", pid);
        item.put("name", p.getName());
        item.put("brand", p.getBrand() != null ? p.getBrand().getBrandName() : "—");
        item.put("article", p.getArticleCode());
        item.put("folderId", sectionId);
        item.put("qty", qty);
        item.put("coeff", coeff);
        item.put("basePrice", basePrice);
        item.put("price", basePrice * coeff);
        return item;
    }
}
