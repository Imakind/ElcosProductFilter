package com.example.productfilter.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.math.BigDecimal;


@Entity
@Table(name="products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer productId;

    private String name;

    @ManyToOne
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(name = "base_price")
    private BigDecimal price;

    @Column(name = "article_code", nullable = false)
    private String articleCode;

    @ManyToOne
    @JoinColumn(name = "supplier_id")
    private Supplier supplier;
    private LocalDateTime importedAt;

    @Column(name = "import_price_date")
    private String importPriceDate; //dd:mm:yyyy

    // + геттер и сеттер
    public String getImportPriceDate() {
        return importPriceDate;
    }

    public void setImportPriceDate(String importPriceDate) {
        this.importPriceDate = importPriceDate;
    }

    public LocalDateTime getImportedAt() {
        return importedAt;
    }

    public void setImportedAt(LocalDateTime importedAt) {
        this.importedAt = importedAt;
    }

    public Supplier getSupplier() {
        return supplier;
    }

    public void setSupplier(Supplier supplier) {
        this.supplier = supplier;
    }


    // И геттер-сеттер
    public String getArticleCode() {
        return articleCode;
    }

    public void setArticleCode(String articleCode) {
        this.articleCode = articleCode;
    }


    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getProductId() {
        return productId;
    }

    public void setProductId(Integer productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Brand getBrand() {
        return brand;
    }

    public void setBrand(Brand brand) {
        this.brand = brand;
    }
}

