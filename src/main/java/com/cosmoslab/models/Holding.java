package com.cosmoslab.models;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a single stock holding within an Account's portfolio.
 * Embedded in the Account document (denormalized for read performance).
 */
public class Holding {

    @JsonProperty("ticker")
    private String ticker;

    @JsonProperty("companyName")
    private String companyName;

    @JsonProperty("shares")
    private int shares;

    @JsonProperty("avgCostBasis")
    private double avgCostBasis;

    @JsonProperty("currentPrice")
    private double currentPrice;

    @JsonProperty("marketValue")
    private double marketValue;

    @JsonProperty("gainLoss")
    private double gainLoss;

    @JsonProperty("gainLossPercent")
    private double gainLossPercent;

    public Holding() {}

    public Holding(String ticker, String companyName, int shares, double avgCostBasis,
                   double currentPrice) {
        this.ticker = ticker;
        this.companyName = companyName;
        this.shares = shares;
        this.avgCostBasis = avgCostBasis;
        this.currentPrice = currentPrice;
        this.marketValue = shares * currentPrice;
        this.gainLoss = this.marketValue - (shares * avgCostBasis);
        this.gainLossPercent = ((currentPrice - avgCostBasis) / avgCostBasis) * 100.0;
    }

    // --- Getters and Setters ---

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public int getShares() { return shares; }
    public void setShares(int shares) { this.shares = shares; }

    public double getAvgCostBasis() { return avgCostBasis; }
    public void setAvgCostBasis(double avgCostBasis) { this.avgCostBasis = avgCostBasis; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public double getMarketValue() { return marketValue; }
    public void setMarketValue(double marketValue) { this.marketValue = marketValue; }

    public double getGainLoss() { return gainLoss; }
    public void setGainLoss(double gainLoss) { this.gainLoss = gainLoss; }

    public double getGainLossPercent() { return gainLossPercent; }
    public void setGainLossPercent(double gainLossPercent) { this.gainLossPercent = gainLossPercent; }

    @Override
    public String toString() {
        return String.format("Holding{ticker='%s', shares=%d, value=%.2f}", ticker, shares, marketValue);
    }
}
