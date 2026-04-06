package com.cosmoslab.lab05;

import com.azure.cosmos.*;
import com.azure.cosmos.models.*;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.cosmoslab.common.Config;
import com.cosmoslab.common.CosmosClientFactory;
import com.cosmoslab.models.Account;
import com.cosmoslab.models.Stock;

import java.util.Arrays;

/**
 * Lab 5: Point Reads and Query Options via SDK.
 *
 * Demonstrates:
 *   1. Point read (readItem) — fastest, 1 RU/KB
 *   2. Single-partition parameterized query
 *   3. Cross-partition query (sector filter)
 *   4. Embedded array query (accounts holding a specific stock)
 *   5. Pagination with continuation tokens (small page size)
 */
public class QueryDemo {

    public static void main(String[] args) {
        CosmosClient client = null;
        try {
            client = CosmosClientFactory.getClient();
            CosmosDatabase database = client.getDatabase(Config.getDatabaseName());
            CosmosContainer stocksContainer = database.getContainer(Config.getStocksContainer());
            CosmosContainer accountsContainer = database.getContainer(Config.getAccountsContainer());

            System.out.println("\n=== Lab 5: Point Reads & Query Options ===\n");

            // --- 1. Point Read ---
            demoPointRead(stocksContainer);

            // --- 2. Single-Partition Query ---
            demoSinglePartitionQuery(accountsContainer);

            // --- 3. Cross-Partition Query (stocks by sector) ---
            demoCrossPartitionQuery(stocksContainer, "Technology");

            // --- 4. Embedded array query (who holds NVDA?) ---
            demoPortfolioQuery(accountsContainer, "NVDA");

            // --- 5. Pagination with continuation tokens ---
            demoPagination(stocksContainer);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            CosmosClientFactory.closeClient();
        }
    }

    // ========================================================================
    // 1. Point Read — the most efficient operation
    // ========================================================================
    private static void demoPointRead(CosmosContainer stocksContainer) {
        System.out.println("--- 1. Point Read: Stock AAPL ---");

        try {
            // BEST PRACTICE: Use readItem when you know both id AND partition key
            CosmosItemResponse<Stock> response = stocksContainer.readItem(
                    "STK-001",                          // Document id
                    new PartitionKey("AAPL"),            // Partition key value
                    Stock.class);

            Stock stock = response.getItem();
            System.out.printf("Stock: %s - %s - $%.2f%n",
                    stock.getTicker(), stock.getCompanyName(), stock.getCurrentPrice());
            System.out.printf("  Sector: %s | P/E: %.1f | Dividend: %.2f%%%n",
                    stock.getSector(), stock.getPeRatio(), stock.getDividendYield());
            System.out.printf("  RU cost: %.2f%n%n", response.getRequestCharge());

        } catch (CosmosException e) {
            System.err.println("Point read failed: " + e.getMessage());
        }
    }

    // ========================================================================
    // 2. Single-Partition Query — efficient, targets one partition
    // ========================================================================
    private static void demoSinglePartitionQuery(CosmosContainer accountsContainer) {
        System.out.println("--- 2. Single-Partition Query: Account ACC-001 ---");

        // BEST PRACTICE: Always parameterize queries
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT * FROM c WHERE c.accountId = @accountId",
                Arrays.asList(new SqlParameter("@accountId", "ACC-001")));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        double totalRU = 0;
        for (FeedResponse<Account> page : accountsContainer
                .queryItems(querySpec, options, Account.class)
                .iterableByPage()) {

            totalRU += page.getRequestCharge();
            for (Account account : page.getResults()) {
                System.out.printf("Account: %s - %s (%s)%n",
                        account.getAccountId(), account.getOwnerName(), account.getRiskProfile());
                System.out.printf("  Total Value: $%,.2f | Cash: $%,.2f%n",
                        account.getTotalValue(), account.getCashBalance());

                if (account.getPortfolio() != null) {
                    System.out.printf("  Portfolio: %d holdings%n", account.getPortfolio().size());
                    account.getPortfolio().forEach(h ->
                            System.out.printf("    %-6s | %4d shares | $%,10.2f%n",
                                    h.getTicker(), h.getShares(), h.getMarketValue()));
                }
            }
        }
        System.out.printf("  RU cost: %.2f%n%n", totalRU);
    }

    // ========================================================================
    // 3. Cross-Partition Query — filters on non-partition-key field
    // ========================================================================
    private static void demoCrossPartitionQuery(CosmosContainer stocksContainer, String sector) {
        System.out.printf("--- 3. Cross-Partition Query: %s stocks ---%n", sector);

        // BEST PRACTICE: Use projections (SELECT only needed fields) to reduce RU
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT c.ticker, c.companyName, c.currentPrice, c.peRatio, c.marketCap " +
                "FROM c WHERE c.sector = @sector ORDER BY c.currentPrice DESC",
                Arrays.asList(new SqlParameter("@sector", sector)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
        // Cross-partition queries are enabled by default in SDK v4

        double totalRU = 0;
        int count = 0;

        for (FeedResponse<Stock> page : stocksContainer
                .queryItems(querySpec, options, Stock.class)
                .iterableByPage()) {

            totalRU += page.getRequestCharge();
            for (Stock stock : page.getResults()) {
                count++;
                System.out.printf("  %-6s - %-30s - $%,10.2f%n",
                        stock.getTicker(), stock.getCompanyName(), stock.getCurrentPrice());
            }
        }
        System.out.printf("Found %d %s stocks. RU cost: %.2f (cross-partition fan-out)%n%n",
                count, sector, totalRU);
    }

    // ========================================================================
    // 4. Embedded Array Query — who holds a specific stock?
    // ========================================================================
    private static void demoPortfolioQuery(CosmosContainer accountsContainer, String ticker) {
        System.out.printf("--- 4. Accounts holding %s ---%n", ticker);

        // JOIN iterates the embedded portfolio array within each account document
        // This is NOT a join between containers — it's within a single document
        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT c.accountId, c.ownerName, c.accountType, " +
                "       h.ticker, h.shares, h.marketValue, h.gainLossPercent " +
                "FROM c JOIN h IN c.portfolio " +
                "WHERE h.ticker = @ticker",
                Arrays.asList(new SqlParameter("@ticker", ticker)));

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        double totalRU = 0;
        int count = 0;

        for (FeedResponse<com.fasterxml.jackson.databind.JsonNode> page : accountsContainer
                .queryItems(querySpec, options, com.fasterxml.jackson.databind.JsonNode.class)
                .iterableByPage()) {

            totalRU += page.getRequestCharge();
            for (com.fasterxml.jackson.databind.JsonNode node : page.getResults()) {
                count++;
                System.out.printf("  %-8s %-25s | %5d shares | $%,12.2f | %+.2f%%%n",
                        node.get("accountId").asText(),
                        node.get("ownerName").asText(),
                        node.get("shares").asInt(),
                        node.get("marketValue").asDouble(),
                        node.get("gainLossPercent").asDouble());
            }
        }
        System.out.printf("Found %d accounts with %s. RU cost: %.2f%n%n", count, ticker, totalRU);
    }

    // ========================================================================
    // 5. Pagination with Continuation Tokens
    // ========================================================================
    private static void demoPagination(CosmosContainer stocksContainer) {
        System.out.println("--- 5. Pagination Demo (5 items per page) ---");

        SqlQuerySpec querySpec = new SqlQuerySpec(
                "SELECT c.ticker, c.companyName, c.currentPrice, c.sector " +
                "FROM c ORDER BY c.currentPrice DESC");

        CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();

        // Set small page size to demonstrate pagination
        int pageSize = 5;
        int maxPages = 4;   // Only show first 4 pages for demo
        String continuationToken = null;
        int pageNum = 0;
        double totalRU = 0;

        do {
            pageNum++;

            // BEST PRACTICE: Guard against empty continuation tokens
            // Pass null for first page, never pass empty string ""
            Iterable<FeedResponse<Stock>> pages;
            if (continuationToken != null && !continuationToken.isEmpty()) {
                pages = stocksContainer
                        .queryItems(querySpec, options, Stock.class)
                        .iterableByPage(continuationToken, pageSize);
            } else {
                pages = stocksContainer
                        .queryItems(querySpec, options, Stock.class)
                        .iterableByPage(pageSize);
            }

            for (FeedResponse<Stock> page : pages) {
                totalRU += page.getRequestCharge();

                System.out.printf("--- Page %d (RU: %.2f) ---%n", pageNum, page.getRequestCharge());
                for (Stock stock : page.getResults()) {
                    System.out.printf("  %-6s %-30s $%,10.2f  [%s]%n",
                            stock.getTicker(),
                            stock.getCompanyName(),
                            stock.getCurrentPrice(),
                            stock.getSector());
                }

                continuationToken = page.getContinuationToken();

                // Only process one page per iteration
                break;
            }

            if (pageNum >= maxPages) {
                System.out.printf("  (... stopping after %d pages for demo, " +
                        "%s more pages available)%n",
                        maxPages, continuationToken != null ? "has" : "no");
                break;
            }

        } while (continuationToken != null);

        System.out.printf("Total RU for %d pages: %.2f%n", pageNum, totalRU);
        System.out.println("\nKey takeaway: Each page costs roughly the same RU,");
        System.out.println("unlike OFFSET/LIMIT where cost grows linearly with offset.\n");
    }
}
