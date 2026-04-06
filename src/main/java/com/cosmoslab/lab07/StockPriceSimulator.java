package com.cosmoslab.lab07;

import com.azure.cosmos.*;
import com.azure.cosmos.models.*;
import com.cosmoslab.common.Config;
import com.cosmoslab.common.CosmosClientFactory;
import com.cosmoslab.models.StockTimeSeries;

import java.time.Instant;
import java.util.*;

/**
 * Lab 7: Stock Price Simulator — generates immutable price tick inserts
 * into the stock_time_series container.
 *
 * Run this AFTER starting ChangeFeedProcessorApp.
 */
public class StockPriceSimulator {

    // Stocks to simulate price changes for
    private static final String[] TICKERS = {"AAPL", "MSFT", "GOOGL", "NVDA", "JPM"};

    // Stock IDs mapping (must match data/stocks.json)
    private static final Map<String, String> STOCK_IDS = Map.of(
            "AAPL", "STK-001",
            "MSFT", "STK-002",
            "GOOGL", "STK-003",
            "NVDA", "STK-005",
            "JPM", "STK-009"
    );

    // Starting prices (from our sample data)
    private static final Map<String, Double> PRICES = new HashMap<>(Map.of(
            "AAPL", 189.84,
            "MSFT", 425.22,
            "GOOGL", 175.98,
            "NVDA", 878.35,
            "JPM", 198.42
    ));

    public static void main(String[] args) throws InterruptedException {
        CosmosClient client = null;
        try {
            client = CosmosClientFactory.getClient();
            CosmosDatabase database = client.getDatabase(Config.getDatabaseName());

            // Create stock_time_series container if it doesn't exist
            String tsContainerName = Config.getTimeSeriesContainer();
            CosmosContainerProperties tsProps =
                    new CosmosContainerProperties(tsContainerName, "/ticker");
            database.createContainerIfNotExists(tsProps);
            CosmosContainer timeSeriesContainer = database.getContainer(tsContainerName);

            System.out.println("\n=== Stock Price Simulator ===");
            System.out.println("Simulating price changes for: " +
                    String.join(", ", TICKERS));
            System.out.println("Inserting into: " + tsContainerName + "\n");

            Random random = new Random(42);
            int totalInserts = 0;
            double totalRU = 0;
            int rounds = 5;

            for (int round = 1; round <= rounds; round++) {
                System.out.printf("Round %d/%d:%n", round, rounds);

                for (String ticker : TICKERS) {
                    double previousPrice = PRICES.get(ticker);

                    // Random price change: -2% to +2%
                    double changePercent = (random.nextDouble() * 0.04) - 0.02;
                    double newPrice = Math.round((previousPrice * (1 + changePercent)) * 100.0) / 100.0;

                    // Random volume
                    long volume = 100000 + random.nextInt(5000000);

                    // Create immutable time-series document
                    String id = UUID.randomUUID().toString();
                    String timestamp = Instant.now().toString();

                    StockTimeSeries tick = new StockTimeSeries(
                            id, ticker, newPrice, previousPrice, volume, timestamp);

                    // Insert into stock_time_series container
                    CosmosItemRequestOptions options = new CosmosItemRequestOptions();
                    CosmosItemResponse<StockTimeSeries> response =
                            timeSeriesContainer.createItem(
                                    tick, new PartitionKey(ticker), options);

                    totalRU += response.getRequestCharge();
                    totalInserts++;

                    System.out.printf("  [INSERT] %s: $%.2f -> $%.2f (%+.2f%%) | RU: %.2f | %s%n",
                            ticker, previousPrice, newPrice,
                            changePercent * 100, response.getRequestCharge(),
                            timestamp);

                    // Update local price tracker
                    PRICES.put(ticker, newPrice);
                }

                if (round < rounds) {
                    System.out.println("  Waiting 2 seconds...\n");
                    Thread.sleep(2000);
                }
            }

            System.out.printf("%n=== Simulation Complete ===%n");
            System.out.printf("Total inserts: %d%n", totalInserts);
            System.out.printf("Total RU: %.2f%n", totalRU);
            System.out.println("\nFinal prices:");
            for (String ticker : TICKERS) {
                System.out.printf("  %s: $%.2f%n", ticker, PRICES.get(ticker));
            }

            System.out.println("\nCheck the Change Feed Processor terminal to see updates propagating.");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            CosmosClientFactory.closeClient();
        }
    }
}
