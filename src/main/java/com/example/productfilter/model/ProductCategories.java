package com.example.productfilter.model;

import jakarta.persistence.*;

@Entity
@IdClass(ProductCategoriesId.class)
public class ProductCategories {
    @Id
    private Integer productId;
    @Id
    private Integer categoryId;
}

