package com.cosmoslab.models;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Stock time-series document for the 'stock_time_series' container.
 * Partition key: /ticker
 *
 * Each document represents a price snapshot at a point in time.
 * These are immutable inserts (append-only time-series pattern).
 */
public class StockTimeSeries {

    @JsonProperty("id")
    private String id;

    @JsonProperty("ticker")
    private String ticker;

    @JsonProperty("price")
    private double price;

    @JsonProperty("previousPrice")
    private double previousPrice;

    @JsonProperty("priceChange")
    private double priceChange;

    @JsonProperty("priceChangePercent")
    private double priceChangePercent;

    @JsonProperty("volume")
    private long volume;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("type")
    private String type = "price_tick";

    public StockTimeSeries() {}

    public StockTimeSeries(String id, String ticker, double price, double previousPrice, long volume,
                           String timestamp) {
        this.id = id;
        this.ticker = ticker;
        this.price = price;
        this.previousPrice = previousPrice;
        this.priceChange = price - previousPrice;
        this.priceChangePercent = previousPrice > 0
                ? ((price - previousPrice) / previousPrice) * 100.0
                : 0.0;
        this.volume = volume;
        this.timestamp = timestamp;
    }

    // --- Getters and Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public double getPreviousPrice() { return previousPrice; }
    public void setPreviousPrice(double previousPrice) { this.previousPrice = previousPrice; }

    public double getPriceChange() { return priceChange; }
    public void setPriceChange(double priceChange) { this.priceChange = priceChange; }

    public double getPriceChangePercent() { return priceChangePercent; }
    public void setPriceChangePercent(double priceChangePercent) { this.priceChangePercent = priceChangePercent; }

    public long getVolume() { return volume; }
    public void setVolume(long volume) { this.volume = volume; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    @Override
    public String toString() {
        return String.format("PriceTick{ticker='%s', price=%.2f, change=%.2f%%, time='%s'}",
                ticker, price, priceChangePercent, timestamp);
    }
}
