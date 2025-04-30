package com.example.productfilter.dto;

public class ProductEditDto {
    private Integer productId;
    private String name;
    private Double price;
    private String articleCode;

    // геттеры и сеттеры
    public Integer getProductId() { return productId; }
    public void setProductId(Integer productId) { this.productId = productId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }

    public String getArticleCode() { return articleCode; }
    public void setArticleCode(String articleCode) { this.articleCode = articleCode; }
}
