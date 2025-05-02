package com.example.productfilter.dto;

public class ProposalHistoryView {
    private String name;
    private Double totalSum;
    private String filePath;
    private String formattedTimestamp;

    public ProposalHistoryView(String name, Double totalSum, String filePath, String formattedTimestamp) {
        this.name = name;
        this.totalSum = totalSum;
        this.filePath = filePath;
        this.formattedTimestamp = formattedTimestamp;
    }

    public String getName() {
        return name;
    }

    public Double getTotalSum() {
        return totalSum;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFormattedTimestamp() {
        return formattedTimestamp;
    }
}
