package com.example.productfilter.model;

import jakarta.persistence.*;
@Entity
@Table(name="brands")
public class Brand {
    @Id
    private Integer brandId;

    private String brandName;

    public Integer getBrandId() {
        return brandId;
    }

    public void setBrandId(Integer brandId) {
        this.brandId = brandId;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
    }
}