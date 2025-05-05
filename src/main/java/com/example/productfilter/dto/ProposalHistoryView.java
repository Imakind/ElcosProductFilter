package com.example.productfilter.dto;

public class ProposalHistoryView {
    private Long id;
    private String name;
    private Double totalSum;
    private String filePath;
    private String formattedTimestamp;
    private Long proposalId;

    // Конструктор
    public ProposalHistoryView(Long id, String name, Double totalSum, String filePath, String formattedTimestamp) {
        this.id = id;
        this.name = name;
        this.totalSum = totalSum;
        this.filePath = filePath;
        this.formattedTimestamp = formattedTimestamp;
    }

    // Геттеры
    public Long getProposalId() {
        return proposalId;
    }

    public void setProposalId(Long proposalId) {
        this.proposalId = proposalId;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Double getTotalSum() { return totalSum; }
    public String getFilePath() { return filePath; }
    public String getFormattedTimestamp() { return formattedTimestamp; }
}
