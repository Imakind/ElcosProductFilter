package com.example.productfilter.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "proposal_node_items")
public class ProposalNodeItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false) @JoinColumn(name = "node_id")
    private ProposalNode node;

    @ManyToOne(optional = false) @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, precision = 14, scale = 3)
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal coefficient = BigDecimal.ONE;

    @Column(name = "unit_price", precision = 14, scale = 2)
    private BigDecimal unitPrice; // можно фиксировать «снятую» цену

    @Column
    private String note;

    // getters/setters
    public Long getId() { return id; }
    public ProposalNode getNode() { return node; }
    public void setNode(ProposalNode node) { this.node = node; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public BigDecimal getCoefficient() { return coefficient; }
    public void setCoefficient(BigDecimal coefficient) { this.coefficient = coefficient; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
