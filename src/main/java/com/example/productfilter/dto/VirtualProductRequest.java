// dto/VirtualProductRequest.java
package com.example.productfilter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VirtualProductRequest {
    private int unitPriceTenge; // <-- ДОБАВИТЬ


    private double euroRate;
    private double heightMm;
    private double widthMm;
    private double depthMm;
    private double thicknessMm;

    private double frontBackQty;
    private double sideQty;
    private double topBottomQty;
    private double falsePanelQty;
    private double innerQty;

    private double paintKgPerM2;
    private double paintPrice;
    private double metalPrice;
    private double complexityK;


    // ===== getters/setters =====
    public double getEuroRate() { return euroRate; }
    public void setEuroRate(double euroRate) { this.euroRate = euroRate; }

    public double getHeightMm() { return heightMm; }
    public void setHeightMm(double heightMm) { this.heightMm = heightMm; }

    public double getWidthMm() { return widthMm; }
    public void setWidthMm(double widthMm) { this.widthMm = widthMm; }

    public double getDepthMm() { return depthMm; }
    public void setDepthMm(double depthMm) { this.depthMm = depthMm; }

    public double getThicknessMm() { return thicknessMm; }
    public void setThicknessMm(double thicknessMm) { this.thicknessMm = thicknessMm; }

    public double getFrontBackQty() { return frontBackQty; }
    public void setFrontBackQty(double frontBackQty) { this.frontBackQty = frontBackQty; }

    public double getSideQty() { return sideQty; }
    public void setSideQty(double sideQty) { this.sideQty = sideQty; }

    public double getTopBottomQty() { return topBottomQty; }
    public void setTopBottomQty(double topBottomQty) { this.topBottomQty = topBottomQty; }

    public double getFalsePanelQty() { return falsePanelQty; }
    public void setFalsePanelQty(double falsePanelQty) { this.falsePanelQty = falsePanelQty; }

    public double getInnerQty() { return innerQty; }
    public void setInnerQty(double innerQty) { this.innerQty = innerQty; }

    public double getPaintKgPerM2() { return paintKgPerM2; }
    public void setPaintKgPerM2(double paintKgPerM2) { this.paintKgPerM2 = paintKgPerM2; }

    public double getPaintPrice() { return paintPrice; }
    public void setPaintPrice(double paintPrice) { this.paintPrice = paintPrice; }

    public double getMetalPrice() { return metalPrice; }
    public void setMetalPrice(double metalPrice) { this.metalPrice = metalPrice; }

    public double getComplexityK() { return complexityK; }
    public void setComplexityK(double complexityK) { this.complexityK = complexityK; }

    public Results getResults() { return results; }
    public void setResults(Results results) { this.results = results; }
    public int getUnitPriceTenge() { return unitPriceTenge; }
    public void setUnitPriceTenge(int unitPriceTenge) { this.unitPriceTenge = unitPriceTenge; }

    private Results results;

    public static class Results {
        private int finalPriceTenge; // <-- ДОБАВИТЬ (если хочешь как в payload)
        public int getFinalPriceTenge() { return finalPriceTenge; }
        public void setFinalPriceTenge(int finalPriceTenge) { this.finalPriceTenge = finalPriceTenge; }

        // оставь что есть
        private int costTenge;
        private double areaM2;
        private double massKg;
        private double costEuro;

        public double getAreaM2() { return areaM2; }
        public void setAreaM2(double areaM2) { this.areaM2 = areaM2; }

        public double getMassKg() { return massKg; }
        public void setMassKg(double massKg) { this.massKg = massKg; }

        public int getCostTenge() { return costTenge; }
        public void setCostTenge(int costTenge) { this.costTenge = costTenge; }

        public double getCostEuro() { return costEuro; }
        public void setCostEuro(double costEuro) { this.costEuro = costEuro; }
    }

    @Override
    public String toString() {
        return "VirtualProductRequest{" +
                "euroRate=" + euroRate +
                ", heightMm=" + heightMm +
                ", widthMm=" + widthMm +
                ", depthMm=" + depthMm +
                ", thicknessMm=" + thicknessMm +
                ", frontBackQty=" + frontBackQty +
                ", sideQty=" + sideQty +
                ", topBottomQty=" + topBottomQty +
                ", falsePanelQty=" + falsePanelQty +
                ", innerQty=" + innerQty +
                ", paintKgPerM2=" + paintKgPerM2 +
                ", paintPrice=" + paintPrice +
                ", metalPrice=" + metalPrice +
                ", complexityK=" + complexityK +
                ", results=" + (results != null ?
                ("{areaM2=" + results.areaM2 + ", massKg=" + results.massKg +
                        ", costTenge=" + results.costTenge + ", costEuro=" + results.costEuro + "}")
                : null) +
                '}';
    }
}
