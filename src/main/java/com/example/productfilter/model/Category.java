package com.example.productfilter.model;


import jakarta.persistence.*;
@Entity
@Table(name="categories")
public class Category {
    @Id
    private Integer categoryId;

    private Integer parentCategoryId;

    private String name;


    public Integer getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public Integer getParentCategoryId() {
        return parentCategoryId;
    }

    public void setParentCategoryId(Integer parentCategoryId) {
        this.parentCategoryId = parentCategoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}