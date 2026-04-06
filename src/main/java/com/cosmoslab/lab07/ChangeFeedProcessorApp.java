package com.cosmoslab.lab07;

import com.azure.cosmos.*;
import com.azure.cosmos.models.*;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Lab 7: Change Feed Processor — reads price tick changes from stock_time_series
 * and updates the stocks container with latest price and timestamp.
 *
 * Run this BEFORE StockPriceSimulator.
 *
 * Uses the async CosmosClient for Change Feed Processor (required by the SDK).
 */
public class ChangeFeedProcessorApp {

    // Stock ID mapping — must match the ids in data/stocks.json
    private static final Map<String, String> STOCK_IDS = Map.of(
            "AAPL", "STK-001",
            "MSFT", "STK-002",
            "GOOGL", "STK-003",
            "AMZN", "STK-004",
            "NVDA", "STK-005",
            "META", "STK-006",
            "TSLA", "STK-007",
            "JPM", "STK-009",
            "JNJ", "STK-010"
    );

    public static void main(String[] args) {
        // Change Feed Processor requires the ASYNC client
        CosmosAsyncClient asyncClient = null;

        try {
            // Load config
            String endpoint = getProperty("cosmos.endpoint");
            String key = getProperty("cosmos.key");
            String connectionMode = getProperty("cosmos.connection.mode");
            String databaseName = getProperty("cosmos.database");
            String timeSeriesContainerName = getProperty("cosmos.container.timeseries");
            String leasesContainerName = getProperty("cosmos.container.leases");
            String stocksContainerName = getProperty("cosmos.container.stocks");

            // Build the async client
            CosmosClientBuilder builder = new CosmosClientBuilder()
                    .endpoint(endpoint)
                    .key(key)
                    .consistencyLevel(ConsistencyLevel.SESSION)
                    .contentResponseOnWriteEnabled(true);

            if ("GATEWAY".equalsIgnoreCase(connectionMode)) {
                builder.gatewayMode();
            } else {
                builder.directMode();
            }

            asyncClient = builder.buildAsyncClient();

            // Get async database and container references
            CosmosAsyncDatabase database = asyncClient.getDatabase(databaseName);
            CosmosAsyncContainer timeSeriesContainer = database.getContainer(timeSeriesContainerName);
            CosmosAsyncContainer stocksContainer = database.getContainer(stocksContainerName);

            // Create leases container (used for checkpoint tracking)
            CosmosContainerProperties leaseProps =
                    new CosmosContainerProperties(leasesContainerName, "/id");
            database.createContainerIfNotExists(leaseProps).block();
            CosmosAsyncContainer leaseContainer = database.getContainer(leasesContainerName);

            // Create stock_time_series container if not exists
            CosmosContainerProperties tsProps =
                    new CosmosContainerProperties(timeSeriesContainerName, "/ticker");
            database.createContainerIfNotExists(tsProps).block();

            System.out.println("\n=== Change Feed Processor ===");
            System.out.println("Source: " + timeSeriesContainerName);
            System.out.println("Target: " + stocksContainerName);
            System.out.println("Leases: " + leasesContainerName);

            // Build the Change Feed Processor
            String hostName = "workshop-processor-" + ProcessHandle.current().pid();

            // Capture stocksContainer for use in lambda
            final CosmosAsyncContainer stocksRef = stocksContainer;

            ChangeFeedProcessor processor = new ChangeFeedProcessorBuilder()
                    .hostName(hostName)
                    .feedContainer(timeSeriesContainer)
                    .leaseContainer(leaseContainer)
                    .handleChanges((List<JsonNode> changes, ChangeFeedProcessorContext context) -> {
                        System.out.printf("%n[CFP] Processing %d changes...%n", changes.size());

                        for (JsonNode change : changes) {
                            try {
                                String ticker = change.get("ticker").asText();
                                double newPrice = change.get("price").asDouble();
                                String timestamp = change.get("timestamp").asText();
                                String type = change.has("type") ? change.get("type").asText() : "";

                                // Only process price tick documents
                                if (!"price_tick".equals(type)) {
                                    continue;
                                }

                                // Find the stock document ID
                                String stockId = STOCK_IDS.get(ticker);
                                if (stockId == null) {
                                    System.out.printf("[CFP] Unknown ticker: %s (skipped)%n", ticker);
                                    continue;
                                }

                                // Update the stock document using Patch (no read needed!)
                                CosmosPatchOperations patchOps = CosmosPatchOperations.create()
                                        .set("/currentPrice", newPrice)
                                        .set("/updTimestamp", timestamp);

                                stocksRef.patchItem(
                                        stockId,
                                        new PartitionKey(ticker),
                                        patchOps,
                                        new CosmosPatchItemRequestOptions(),
                                        JsonNode.class
                                ).block();

                                System.out.printf("[CFP] Updated %s (id=%s): $%.2f at %s%n",
                                        ticker, stockId, newPrice, timestamp);

                            } catch (Exception e) {
                                System.err.printf("[CFP] Error processing change: %s%n",
                                        e.getMessage());
                            }
                        }
                    })
                    .buildChangeFeedProcessor();

            // Start the processor
            System.out.println("\nStarting Change Feed Processor...");
            processor.start().block();
            System.out.println("Change Feed Processor started successfully!");
            System.out.println("Host: " + hostName);
            System.out.println("\nWaiting for changes from stock_time_series container...");
            System.out.println("(Run StockPriceSimulator in another terminal)");
            System.out.println("Press Enter to stop.\n");

            // Wait for user to press Enter
            Scanner scanner = new Scanner(System.in);
            scanner.nextLine();

            // Graceful shutdown
            System.out.println("Stopping Change Feed Processor...");
            processor.stop().block();
            System.out.println("Change Feed Processor stopped.");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (asyncClient != null) {
                asyncClient.close();
            }
        }
    }

    private static String getProperty(String key) {
        try {
            java.util.Properties props = new java.util.Properties();
            try (var is = ChangeFeedProcessorApp.class.getClassLoader()
                    .getResourceAsStream("application.properties")) {
                props.load(is);
            }
            String value = props.getProperty(key);
            if (value == null || value.contains("<")) {
                throw new RuntimeException("Property " + key + " not configured");
            }
            return value;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load property: " + key, e);
        }
    }
}
