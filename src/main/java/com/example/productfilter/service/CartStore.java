package com.example.productfilter.service;

import com.example.productfilter.model.Product;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@SessionScope
public class CartStore implements Serializable {

    // обычные товары: productId -> qty
    private final Map<Integer, Integer> quantities = new LinkedHashMap<>();

    // виртуальные товары: virtualId(негативный) -> Product
    private final Map<Integer, Product> virtualProducts = new LinkedHashMap<>();

    private int virtualSeq = -1;

    public Map<Integer, Integer> getQuantities() {
        return quantities;
    }

    public Map<Integer, Product> getVirtualProducts() {
        return virtualProducts;
    }

    public void add(int productId, int qty) {
        quantities.merge(productId, qty, Integer::sum);
        if (quantities.get(productId) <= 0) {
            quantities.remove(productId);
        }
    }

    public void setQty(int productId, int qty) {
        if (qty <= 0) quantities.remove(productId);
        else quantities.put(productId, qty);
    }

    /** Добавление виртуального товара в корзину */
    public int addVirtual(Product product) {
        int id = virtualSeq--;
        product.setProductId(id);     // чтобы фронт/маппинг видели id
        virtualProducts.put(id, product);
        quantities.put(id, 1);        // виртуальный тоже участвует в qty/sum
        return id;
    }

    public void remove(int productId) {
        quantities.remove(productId);
        virtualProducts.remove(productId);
    }

    public boolean isVirtual(int productId) {
        return productId < 0;
    }

    public void clear() {
        quantities.clear();
        virtualProducts.clear();
        virtualSeq = -1;
    }
}
