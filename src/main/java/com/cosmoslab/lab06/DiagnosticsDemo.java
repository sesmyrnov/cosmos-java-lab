package com.cosmoslab.lab06;

import com.azure.cosmos.*;
import com.azure.cosmos.models.*;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.cosmoslab.common.Config;
import com.cosmoslab.common.CosmosClientFactory;
import com.cosmoslab.models.Stock;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;

/**
 * Lab 6: SDK Diagnostics & Logging.
 *
 * Demonstrates:
 *   1. Capturing CosmosDiagnostics on point reads
 *   2. Aggregating diagnostics across query pages
 *   3. Error diagnostics (404, etc.)
 *   4. Simulating 429 throttling with rapid bulk inserts
 */
public class DiagnosticsDemo {

    public static void main(String[] args) {
        CosmosClient client = null;
        try {
            client = CosmosClientFactory.getClient();
            CosmosDatabase database = client.getDatabase(Config.getDatabaseName());
            CosmosContainer stocksContainer = database.getContainer(Config.getStocksContainer());

            System.out.println("\n=== Lab 6: SDK Diagnostics & Logging ===\n");

            // Part 1: Point Read diagnostics
            demoPointReadDiagnostics(stocksContainer);

            // Part 2: Query diagnostics
            demoQueryDiagnostics(stocksContainer);

            // Part 3: Error diagnostics
            demoErrorDiagnostics(stocksContainer);

            // Part 4: Simulate 429 throttling
            demoThrottlingSimulation(stocksContainer);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            CosmosClientFactory.closeClient();
        }
    }

    // ========================================================================
    // Part 1: Point Read — capture full diagnostics
    // ========================================================================
    private static void demoPointReadDiagnostics(CosmosContainer container) {
        System.out.println("=== Part 1: Point Read Diagnostics ===\n");

        try {
            CosmosItemResponse<Stock> response = container.readItem(
                    "STK-001", new PartitionKey("AAPL"), Stock.class);

            // Basic metrics from the response
            System.out.println("--- Operation Metrics ---");
            System.out.printf("  Request Charge:  %.2f RU%n", response.getRequestCharge());
            System.out.printf("  Status Code:     %d%n", response.getStatusCode());
            System.out.printf("  Activity ID:     %s%n", response.getActivityId());

            // Diagnostics object contains detailed timing and connection info
            CosmosDiagnostics diagnostics = response.getDiagnostics();
            System.out.printf("  Client Latency:  %d ms%n", diagnostics.getDuration().toMillis());

            // Full diagnostic string — shows detailed breakdown
            System.out.println("\n--- Full Diagnostics (first 500 chars) ---");
            String diagStr = diagnostics.toString();
            System.out.println(diagStr.substring(0, Math.min(500, diagStr.length())));
            System.out.println("  ... (truncated for readability)");

        } catch (CosmosException e) {
            System.err.println("Point read failed: " + e.getMessage());
        }
        System.out.println();
    }

    // ========================================================================
    // Part 2: Query — aggregate diagnostics across pages
    // ========================================================================
    private static void demoQueryDiagnostics(CosmosContainer container) {
        System.out.println("=== Part 2: Query Diagnostics ===\n");

        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT c.ticker, c.companyName, c.currentPrice, c.sector " +
                "FROM c WHERE c.sector = @sector ORDER BY c.currentPrice DESC",
                Arrays.asList(new SqlParameter("@sector", "Technology")));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        double totalRU = 0;
        long totalLatencyMs = 0;
        int pageCount = 0;
        int docCount = 0;

        System.out.println("Query: SELECT ... WHERE c.sector = 'Technology' ORDER BY c.currentPrice DESC\n");

        for (FeedResponse<Stock> page : container
                .queryItems(querySpec, options, Stock.class)
                .iterableByPage(10)) {

            pageCount++;
            double pageRU = page.getRequestCharge();
            long pageLatency = page.getCosmosDiagnostics().getDuration().toMillis();
            int pageResults = page.getResults().size();

            totalRU += pageRU;
            totalLatencyMs += pageLatency;
            docCount += pageResults;

            System.out.printf("  Page %d: %d docs | %.2f RU | %d ms%n",
                    pageCount, pageResults, pageRU, pageLatency);
        }

        System.out.println("\n--- Aggregated Query Metrics ---");
        System.out.printf("  Total Pages:     %d%n", pageCount);
        System.out.printf("  Total Documents: %d%n", docCount);
        System.out.printf("  Total RU:        %.2f%n", totalRU);
        System.out.printf("  Total Latency:   %d ms%n", totalLatencyMs);
        System.out.printf("  Avg RU/page:     %.2f%n", totalRU / Math.max(1, pageCount));
        System.out.println();
    }

    // ========================================================================
    // Part 3: Error diagnostics — 404 Not Found
    // ========================================================================
    private static void demoErrorDiagnostics(CosmosContainer container) {
        System.out.println("=== Part 3: Error Diagnostics ===\n");

        try {
            // Intentionally read a non-existent document
            container.readItem("NONEXISTENT-ID",
                    new PartitionKey("NONEXISTENT"),
                    Stock.class);

        } catch (CosmosException e) {
            System.out.println("--- Error Details ---");
            System.out.printf("  Status Code:      %d (%s)%n",
                    e.getStatusCode(), getStatusDescription(e.getStatusCode()));
            System.out.printf("  Sub Status Code:  %d%n", e.getSubStatusCode());
            System.out.printf("  Request Charge:   %.2f RU%n", e.getRequestCharge());
            System.out.printf("  Activity ID:      %s%n", e.getActivityId());
            System.out.printf("  Message:          %s%n",
                    e.getMessage().substring(0, Math.min(100, e.getMessage().length())));

            // Error diagnostics help identify WHY the error occurred
            if (e.getDiagnostics() != null) {
                System.out.printf("  Diagnostics Time: %d ms%n",
                        e.getDiagnostics().getDuration().toMillis());
            }
        }
        System.out.println();
    }

    // ========================================================================
    // Part 4: Simulate 429 Throttling — rapid bulk inserts
    // ========================================================================
    private static void demoThrottlingSimulation(CosmosContainer container) {
        System.out.println("=== Part 4: 429 Throttling Simulation ===\n");
        System.out.println("Inserting 500 temp documents rapidly to trigger throttling...");
        System.out.println("The SDK will automatically retry on 429 errors.\n");

        int totalOps = 500;
        int successCount = 0;
        int errorCount = 0;
        double totalRU = 0;
        long startTime = System.currentTimeMillis();
        long maxLatencyMs = 0;
        int retryIndicators = 0;

        String batchId = UUID.randomUUID().toString().substring(0, 8);

        for (int i = 0; i < totalOps; i++) {
            String id = "TEMP-" + batchId + "-" + String.format("%04d", i);
            String ticker = "TMP" + String.format("%04d", i);

            Stock tempStock = new Stock(
                    id, ticker,
                    "Temp Stock " + i,
                    "Temporary",
                    "Workshop Test",
                    "TEST",
                    10.00 + (i * 0.01),
                    "USD",
                    1000000,
                    10.0,
                    0.0,
                    15.00,
                    5.00
            );

            try {
                long opStart = System.currentTimeMillis();
                CosmosItemResponse<Stock> response = container.createItem(
                        tempStock,
                        new PartitionKey(ticker),
                        new CosmosItemRequestOptions());

                long opLatency = System.currentTimeMillis() - opStart;
                totalRU += response.getRequestCharge();
                successCount++;
                maxLatencyMs = Math.max(maxLatencyMs, opLatency);

                // Check if diagnostics indicate retries occurred
                CosmosDiagnostics diag = response.getDiagnostics();
                if (diag != null && diag.getDuration().toMillis() > 200) {
                    retryIndicators++;
                }

                // Print progress
                if ((i + 1) % 100 == 0) {
                    System.out.printf("  Progress: %d/%d (%.0f RU so far, max latency: %d ms)%n",
                            i + 1, totalOps, totalRU, maxLatencyMs);
                }

            } catch (CosmosException e) {
                errorCount++;
                if (e.getStatusCode() == 429) {
                    retryIndicators++;
                    System.out.printf("  [429] Throttled at operation %d! " +
                            "Retry-after: %s | Diag: %d ms%n",
                            i,
                            e.getRetryAfterDuration(),
                            e.getDiagnostics() != null
                                    ? e.getDiagnostics().getDuration().toMillis() : -1);
                } else {
                    System.err.printf("  [%d] Error at operation %d: %s%n",
                            e.getStatusCode(), i, e.getMessage());
                }
            }
        }

        long totalTimeMs = System.currentTimeMillis() - startTime;

        // --- Cleanup: Delete temp documents ---
        System.out.println("\nCleaning up temp documents...");
        int deleted = 0;
        for (int i = 0; i < totalOps; i++) {
            String id = "TEMP-" + batchId + "-" + String.format("%04d", i);
            String ticker = "TMP" + String.format("%04d", i);
            try {
                container.deleteItem(id, new PartitionKey(ticker),
                        new CosmosItemRequestOptions());
                deleted++;
            } catch (CosmosException e) {
                // Ignore cleanup errors
            }
        }
        System.out.printf("Cleaned up %d temp documents.%n", deleted);

        // --- Summary ---
        System.out.println("\n--- Throttling Simulation Results ---");
        System.out.printf("  Total Operations: %d%n", totalOps);
        System.out.printf("  Successful:       %d%n", successCount);
        System.out.printf("  Failed (429):     %d%n", errorCount);
        System.out.printf("  Retry indicators: %d (ops with >200ms latency)%n", retryIndicators);
        System.out.printf("  Total RU:         %.2f%n", totalRU);
        System.out.printf("  Max Op Latency:   %d ms%n", maxLatencyMs);
        System.out.printf("  Total Time:       %d ms%n", totalTimeMs);
        System.out.printf("  Throughput:        %.1f ops/sec%n",
                (successCount * 1000.0) / totalTimeMs);

        System.out.println("\n--- Key Takeaways ---");
        System.out.println("  1. SDK retries 429 automatically (up to 9 times, 30s max wait)");
        System.out.println("  2. Operations with retries show higher latency in diagnostics");
        System.out.println("  3. If all retries fail, CosmosException with 429 is thrown");
        System.out.println("  4. Monitor 429 rate in production to right-size throughput");
        System.out.println();
    }

    private static String getStatusDescription(int statusCode) {
        return switch (statusCode) {
            case 200 -> "OK";
            case 201 -> "Created";
            case 204 -> "No Content";
            case 304 -> "Not Modified";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict (Duplicate)";
            case 412 -> "Precondition Failed (ETag)";
            case 429 -> "Too Many Requests (Throttled)";
            case 449 -> "Retry With";
            case 500 -> "Internal Server Error";
            case 503 -> "Service Unavailable";
            default -> "Unknown";
        };
    }
}
