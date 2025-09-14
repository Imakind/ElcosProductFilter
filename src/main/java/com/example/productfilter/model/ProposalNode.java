package com.example.productfilter.model;

import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "proposal_nodes")
public class ProposalNode {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false) @JoinColumn(name = "proposal_id")
    private Proposal proposal;

    @ManyToOne @JoinColumn(name = "parent_id")
    private ProposalNode parent;

    @Column(nullable = false)
    private String name;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    // getters/setters
    public Long getId() { return id; }
    public Proposal getProposal() { return proposal; }
    public void setProposal(Proposal p) { this.proposal = p; }
    public ProposalNode getParent() { return parent; }
    public void setParent(ProposalNode parent) { this.parent = parent; }
    public String getName() { return name; }
    public void setName(String n) { this.name = n; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer s) { this.sortOrder = s; }
}
