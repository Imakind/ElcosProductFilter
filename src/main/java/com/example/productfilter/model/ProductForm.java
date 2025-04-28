package com.example.productfilter.model;

public class ProductForm {
    private String name;
    private String articleCode;
    private Integer brandId;
    private Double price;
    private Integer groupId;
    private Integer subGroupId;
    private String param1;
    private String param2;
    private String param3;
    private String param4;
    private String param5;


    public String getArticleCode() {
        return articleCode;
    }
    public void setArticleCode(String articleCode) {
        this.articleCode = articleCode;
    }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getBrandId() { return brandId; }
    public void setBrandId(Integer brandId) { this.brandId = brandId; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public Integer getGroupId() { return groupId; }
    public void setGroupId(Integer groupId) { this.groupId = groupId; }
    public Integer getSubGroupId() { return subGroupId; }
    public void setSubGroupId(Integer subGroupId) { this.subGroupId = subGroupId; }
    public String getParam1() { return param1; }
    public void setParam1(String param1) { this.param1 = param1; }
    public String getParam2() { return param2; }
    public void setParam2(String param2) { this.param2 = param2; }
    public String getParam3() { return param3; }
    public void setParam3(String param3) { this.param3 = param3; }
    public String getParam4() { return param4; }
    public void setParam4(String param4) { this.param4 = param4; }
    public String getParam5() { return param5; }
    public void setParam5(String param5) { this.param5 = param5; }
}
