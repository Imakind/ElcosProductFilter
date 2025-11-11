package com.example.productfilter.model;

import jakarta.persistence.*;

@Entity
public class ProposalItem {
    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    @JoinColumn(name = "proposal_id")
    private Proposal proposal;

    @ManyToOne
    private Product product;

    private Integer quantity;
    private Double basePrice;
    private Double coefficient;
    private Double finalPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private ProposalSection sectionNode;

    // 🔹 «сырое» значение того же столбца (нужно твоему контроллеру для getSectionId())
    @Column(name = "section_id", insertable = false, updatable = false)
    private Long sectionId;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Proposal getProposal() {
        return proposal;
    }

    public void setProposal(Proposal proposal) {
        this.proposal = proposal;
    }

    public Product getProduct() {
        return product;
    }

    public void setProduct(Product product) {
        this.product = product;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Double getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(Double basePrice) {
        this.basePrice = basePrice;
    }

    public Double getCoefficient() {
        return coefficient;
    }

    public void setCoefficient(Double coefficient) {
        this.coefficient = coefficient;
    }

    public Double getFinalPrice() {
        return finalPrice;
    }

    public void setFinalPrice(Double finalPrice) {
        this.finalPrice = finalPrice;
    }

    public ProposalSection getSectionNode() {
        return sectionNode;
    }

    public void setSectionNode(ProposalSection sectionNode) {
        this.sectionNode = sectionNode;
    }

    public Long getSectionId() {
        return sectionId;
    }

    public void setSectionId(Long sectionId) {
        this.sectionId = sectionId;
    }
}
