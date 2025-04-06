package com.example.productfilter.model;

import jakarta.persistence.*;

@Entity
@IdClass(ProductCategoriesId.class)
public class ProductCategories {
    @Id
    private Integer productId;
    @Id
    private Integer categoryId;

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }
}

