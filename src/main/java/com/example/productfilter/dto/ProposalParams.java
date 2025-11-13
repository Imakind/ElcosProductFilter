package com.example.productfilter.dto;

import java.math.BigDecimal;

public class ProposalParams {

    private Boolean vatIncluded;

    private Integer prepaymentPercent;
    private Integer beforeShipmentPercent;
    private Integer postPaymentPercent;

    private String paymentNote;

    private Integer productionDays;
    private String deliveryTerms;
    private String components;

    private Integer warrantyMonths;
    private BigDecimal usdRate;

    private String extraConditions;
    private Integer validDays;

    public Boolean getVatIncluded() {
        return vatIncluded;
    }

    public void setVatIncluded(Boolean vatIncluded) {
        this.vatIncluded = vatIncluded;
    }

    public Integer getPrepaymentPercent() {
        return prepaymentPercent;
    }

    public void setPrepaymentPercent(Integer prepaymentPercent) {
        this.prepaymentPercent = prepaymentPercent;
    }

    public Integer getBeforeShipmentPercent() {
        return beforeShipmentPercent;
    }

    public void setBeforeShipmentPercent(Integer beforeShipmentPercent) {
        this.beforeShipmentPercent = beforeShipmentPercent;
    }

    public Integer getPostPaymentPercent() {
        return postPaymentPercent;
    }

    public void setPostPaymentPercent(Integer postPaymentPercent) {
        this.postPaymentPercent = postPaymentPercent;
    }

    public String getPaymentNote() {
        return paymentNote;
    }

    public void setPaymentNote(String paymentNote) {
        this.paymentNote = paymentNote;
    }

    public Integer getProductionDays() {
        return productionDays;
    }

    public void setProductionDays(Integer productionDays) {
        this.productionDays = productionDays;
    }

    public String getDeliveryTerms() {
        return deliveryTerms;
    }

    public void setDeliveryTerms(String deliveryTerms) {
        this.deliveryTerms = deliveryTerms;
    }

    public String getComponents() {
        return components;
    }

    public void setComponents(String components) {
        this.components = components;
    }

    public Integer getWarrantyMonths() {
        return warrantyMonths;
    }

    public void setWarrantyMonths(Integer warrantyMonths) {
        this.warrantyMonths = warrantyMonths;
    }

    public BigDecimal getUsdRate() {
        return usdRate;
    }

    public void setUsdRate(BigDecimal usdRate) {
        this.usdRate = usdRate;
    }

    public String getExtraConditions() {
        return extraConditions;
    }

    public void setExtraConditions(String extraConditions) {
        this.extraConditions = extraConditions;
    }

    public Integer getValidDays() {
        return validDays;
    }

    public void setValidDays(Integer validDays) {
        this.validDays = validDays;
    }


}

