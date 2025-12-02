// dto/ApplyCoefRequest.java
package com.example.productfilter.dto;

import java.math.BigDecimal;

public class ApplyCoefRequest {
    private String type; // "brand" | "supplier"
    private Integer id;
    private BigDecimal coef;

    public ApplyCoefRequest() {}

    public String getType() {
        return type;
    }
    public void setType(String type) {
        this.type = type;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public BigDecimal getCoef() {
        return coef;
    }
    public void setCoef(BigDecimal coef) {
        this.coef = coef;
    }
}
