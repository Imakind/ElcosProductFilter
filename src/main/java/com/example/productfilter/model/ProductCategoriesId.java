package com.example.productfilter.model;

import java.io.Serializable;
import java.util.Objects;

public class ProductCategoriesId implements Serializable {
    private Integer productId;
    private Integer categoryId;

    public ProductCategoriesId() {}

    public ProductCategoriesId(Integer productId, Integer categoryId) {
        this.productId = productId;
        this.categoryId = categoryId;
    }

    // Обязательно нужно переопределить equals и hashCode

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProductCategoriesId)) return false;
        ProductCategoriesId that = (ProductCategoriesId) o;
        return Objects.equals(productId, that.productId) &&
                Objects.equals(categoryId, that.categoryId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productId, categoryId);
    }
}
