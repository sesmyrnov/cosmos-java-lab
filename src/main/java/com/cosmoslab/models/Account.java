package com.cosmoslab.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Account document model for the 'accounts' container.
 * Partition key: /accountId
 *
 * Each account embeds its portfolio holdings — a common Cosmos DB pattern
 * for data that is always retrieved together (denormalization).
 */
public class Account {

    @JsonProperty("id")
    private String id;

    @JsonProperty("accountId")
    private String accountId;

    @JsonProperty("accountName")
    private String accountName;

    @JsonProperty("accountType")
    private String accountType;  // e.g., "Individual", "IRA", "401k", "Trust"

    @JsonProperty("ownerName")
    private String ownerName;

    @JsonProperty("email")
    private String email;

    @JsonProperty("riskProfile")
    private String riskProfile;  // "Conservative", "Moderate", "Aggressive"

    @JsonProperty("totalValue")
    private double totalValue;

    @JsonProperty("cashBalance")
    private double cashBalance;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("createdDate")
    private String createdDate;

    @JsonProperty("updTimestamp")
    private String updTimestamp;

    @JsonProperty("portfolio")
    private List<Holding> portfolio;

    @JsonProperty("type")
    private String type = "account";

    public Account() {}

    // --- Getters and Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRiskProfile() { return riskProfile; }
    public void setRiskProfile(String riskProfile) { this.riskProfile = riskProfile; }

    public double getTotalValue() { return totalValue; }
    public void setTotalValue(double totalValue) { this.totalValue = totalValue; }

    public double getCashBalance() { return cashBalance; }
    public void setCashBalance(double cashBalance) { this.cashBalance = cashBalance; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getCreatedDate() { return createdDate; }
    public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }

    public String getUpdTimestamp() { return updTimestamp; }
    public void setUpdTimestamp(String updTimestamp) { this.updTimestamp = updTimestamp; }

    public List<Holding> getPortfolio() { return portfolio; }
    public void setPortfolio(List<Holding> portfolio) { this.portfolio = portfolio; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    @Override
    public String toString() {
        return String.format("Account{id='%s', owner='%s', type='%s', value=%.2f, holdings=%d}",
                accountId, ownerName, accountType, totalValue,
                portfolio != null ? portfolio.size() : 0);
    }
}
