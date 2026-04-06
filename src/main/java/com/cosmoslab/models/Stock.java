package com.cosmoslab.models;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Stock document model for the 'stocks' container.
 * Partition key: /ticker
 */
public class Stock {

    @JsonProperty("id")
    private String id;

    @JsonProperty("ticker")
    private String ticker;

    @JsonProperty("companyName")
    private String companyName;

    @JsonProperty("sector")
    private String sector;

    @JsonProperty("industry")
    private String industry;

    @JsonProperty("exchange")
    private String exchange;

    @JsonProperty("currentPrice")
    private double currentPrice;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("marketCap")
    private long marketCap;

    @JsonProperty("peRatio")
    private double peRatio;

    @JsonProperty("dividendYield")
    private double dividendYield;

    @JsonProperty("week52High")
    private double week52High;

    @JsonProperty("week52Low")
    private double week52Low;

    @JsonProperty("updTimestamp")
    private String updTimestamp;

    @JsonProperty("type")
    private String type = "stock";

    public Stock() {}

    public Stock(String id, String ticker, String companyName, String sector, String industry,
                 String exchange, double currentPrice, String currency, long marketCap,
                 double peRatio, double dividendYield, double week52High, double week52Low) {
        this.id = id;
        this.ticker = ticker;
        this.companyName = companyName;
        this.sector = sector;
        this.industry = industry;
        this.exchange = exchange;
        this.currentPrice = currentPrice;
        this.currency = currency;
        this.marketCap = marketCap;
        this.peRatio = peRatio;
        this.dividendYield = dividendYield;
        this.week52High = week52High;
        this.week52Low = week52Low;
    }

    // --- Getters and Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }

    public String getIndustry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public double getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(double currentPrice) { this.currentPrice = currentPrice; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public long getMarketCap() { return marketCap; }
    public void setMarketCap(long marketCap) { this.marketCap = marketCap; }

    public double getPeRatio() { return peRatio; }
    public void setPeRatio(double peRatio) { this.peRatio = peRatio; }

    public double getDividendYield() { return dividendYield; }
    public void setDividendYield(double dividendYield) { this.dividendYield = dividendYield; }

    public double getWeek52High() { return week52High; }
    public void setWeek52High(double week52High) { this.week52High = week52High; }

    public double getWeek52Low() { return week52Low; }
    public void setWeek52Low(double week52Low) { this.week52Low = week52Low; }

    public String getUpdTimestamp() { return updTimestamp; }
    public void setUpdTimestamp(String updTimestamp) { this.updTimestamp = updTimestamp; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    @Override
    public String toString() {
        return String.format("Stock{ticker='%s', company='%s', price=%.2f, sector='%s'}",
                ticker, companyName, currentPrice, sector);
    }
}
