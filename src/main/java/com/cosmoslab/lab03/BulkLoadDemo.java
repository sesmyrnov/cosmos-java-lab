package com.cosmoslab.lab03;

import com.azure.cosmos.*;
import com.azure.cosmos.models.*;
import com.cosmoslab.common.Config;
import com.cosmoslab.common.CosmosClientFactory;
import com.cosmoslab.models.Account;
import com.cosmoslab.models.Stock;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.List;

/**
 * Lab 3: Create Containers and Bulk Load sample data.
 *
 * Creates:
 *   - stocks container (partition key: /ticker)
 *   - accounts container (partition key: /accountId)
 *
 * Loads:
 *   - 100 stock documents from data/stocks.json
 *   - 50 account documents from data/accounts.json
 */
public class BulkLoadDemo {

    public static void main(String[] args) {
        CosmosClient client = null;
        try {
            client = CosmosClientFactory.getClient();
            CosmosDatabase database = client.getDatabase(Config.getDatabaseName());

            System.out.println("\n=== Lab 3: Create Containers & Bulk Load ===\n");

            // --- Create containers ---
            CosmosContainer stocksContainer = createContainerIfNotExists(
                    database, Config.getStocksContainer(), "/ticker");

            CosmosContainer accountsContainer = createContainerIfNotExists(
                    database, Config.getAccountsContainer(), "/accountId");

            // --- Load sample data ---
            ObjectMapper mapper = new ObjectMapper();

            // Load stocks
            System.out.println("Loading stocks from data/stocks.json...");
            List<Stock> stocks = mapper.readValue(
                    new File("data/stocks.json"),
                    new TypeReference<List<Stock>>() {});
            double stocksRU = bulkLoadStocks(stocksContainer, stocks);
            System.out.printf("Loaded %d stocks. Total RU: %.2f (avg %.2f RU/item)%n",
                    stocks.size(), stocksRU, stocksRU / stocks.size());

            // Load accounts
            System.out.println("\nLoading accounts from data/accounts.json...");
            List<Account> accounts = mapper.readValue(
                    new File("data/accounts.json"),
                    new TypeReference<List<Account>>() {});
            double accountsRU = bulkLoadAccounts(accountsContainer, accounts);
            System.out.printf("Loaded %d accounts. Total RU: %.2f (avg %.2f RU/item)%n",
                    accounts.size(), accountsRU, accountsRU / accounts.size());

            // Summary
            System.out.println("\n=== Bulk Load Complete ===");
            System.out.printf("Total documents: %d%n", stocks.size() + accounts.size());
            System.out.printf("Total RU consumed: %.2f%n", stocksRU + accountsRU);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            CosmosClientFactory.closeClient();
        }
    }

    /**
     * Creates a container if it doesn't already exist.
     */
    private static CosmosContainer createContainerIfNotExists(
            CosmosDatabase database, String containerName, String partitionKeyPath) {

        System.out.printf("Creating container: %s (partition key: %s)%n",
                containerName, partitionKeyPath);

        CosmosContainerProperties containerProps =
                new CosmosContainerProperties(containerName, partitionKeyPath);

        // Create container — for Serverless accounts, throughput is automatic
        database.createContainerIfNotExists(containerProps);
        System.out.printf("Container '%s' ready.%n%n", containerName);

        return database.getContainer(containerName);
    }

    /**
     * Loads stock documents into the stocks container.
     * Uses individual createItem calls — sufficient for 100 documents.
     * For 10K+ documents, use CosmosBulkOperations with async client.
     */
    private static double bulkLoadStocks(CosmosContainer container, List<Stock> stocks) {
        double totalRU = 0;
        int count = 0;

        for (Stock stock : stocks) {
            try {
                // BEST PRACTICE: Always create a new CosmosItemRequestOptions per call
                // (never reuse — SDK mutates it internally)
                CosmosItemRequestOptions options = new CosmosItemRequestOptions();

                CosmosItemResponse<Stock> response = container.createItem(
                        stock, new PartitionKey(stock.getTicker()), options);

                totalRU += response.getRequestCharge();
                count++;

                if (count % 25 == 0) {
                    System.out.printf("  ... loaded %d/%d stocks (%.2f RU so far)%n",
                            count, stocks.size(), totalRU);
                }
            } catch (CosmosException e) {
                if (e.getStatusCode() == 409) {
                    // Document already exists — skip (idempotent reload)
                    System.out.printf("  [SKIP] Stock %s already exists%n", stock.getTicker());
                } else {
                    throw e;
                }
            }
        }
        return totalRU;
    }

    /**
     * Loads account documents into the accounts container.
     */
    private static double bulkLoadAccounts(CosmosContainer container, List<Account> accounts) {
        double totalRU = 0;
        int count = 0;

        for (Account account : accounts) {
            try {
                CosmosItemRequestOptions options = new CosmosItemRequestOptions();

                CosmosItemResponse<Account> response = container.createItem(
                        account, new PartitionKey(account.getAccountId()), options);

                totalRU += response.getRequestCharge();
                count++;

                if (count % 10 == 0) {
                    System.out.printf("  ... loaded %d/%d accounts (%.2f RU so far)%n",
                            count, accounts.size(), totalRU);
                }
            } catch (CosmosException e) {
                if (e.getStatusCode() == 409) {
                    System.out.printf("  [SKIP] Account %s already exists%n", account.getAccountId());
                } else {
                    throw e;
                }
            }
        }
        return totalRU;
    }
}
