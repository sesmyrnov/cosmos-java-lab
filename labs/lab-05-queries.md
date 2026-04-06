# Lab 5: Point Reads & Query Options

| Duration | ~20 minutes |
|----------|------------|
| **Goal** | Master point reads, parameterized queries, cross-partition queries, and pagination with continuation tokens |

---

## Key Concepts

### Point Read vs. Query

| | Point Read (`readItem`) | Query (`queryItems`) |
|--|---------------------------|------------------------|
| **When** | You know `id` + partition key | Filter/search/aggregate |
| **RU cost** | ~1 RU per KB | ~2.5+ RU (query engine overhead) |
| **Speed** | Fastest possible | Depends on complexity |
| **Use case** | Get a specific document | Search, filter, reports |

> **Rule:** If you know both `id` and partition key — **always** use `readItem()`.

### Query Scope

| Scope | Filter Includes Partition Key? | Cost |
|-------|-------------------------------|------|
| **Single-partition** | Yes — `WHERE c.accountId = 'ACC-001'` | Low — searches 1 partition |
| **Cross-partition** | No — `WHERE c.sector = 'Technology'` | High — fans out to ALL partitions |

### Parameterized Queries (**Always!**)

```java
// ❌ NEVER — String concatenation (SQL injection risk + no plan caching)
String query = "SELECT * FROM c WHERE c.ticker = '" + ticker + "'";

// ✅ ALWAYS — Parameterized (safe + cached query plans)
SqlQuerySpec querySpec = new SqlQuerySpec(
    "SELECT * FROM c WHERE c.ticker = @ticker",
    new SqlParameter("@ticker", ticker));
```

### Pagination with Continuation Tokens

**Never use OFFSET/LIMIT** for deep pagination — cost grows linearly with offset.

```
Page 1:   OFFSET 0 LIMIT 10     →  10 RU
Page 10:  OFFSET 90 LIMIT 10    →  100 RU (scans 90 + reads 10)
Page 100: OFFSET 990 LIMIT 10   →  1000 RU (scans 990 + reads 10)
```

Instead, use **continuation tokens** — each page costs the same:

```
Page 1:   First request          →  10 RU, returns continuationToken
Page 10:  Token from page 9      →  10 RU (same cost!)
Page 100: Token from page 99     →  10 RU (same cost!)
```

---

## Code Walkthrough

Open [src/main/java/com/cosmoslab/lab05/QueryDemo.java](../src/main/java/com/cosmoslab/lab05/QueryDemo.java)

### 1. Point Read — Single Stock

```java
// Fastest way to get a document — 1 RU per KB
CosmosItemResponse<Stock> response = stocksContainer.readItem(
    "STK-001",                      // id
    new PartitionKey("AAPL"),       // partition key
    Stock.class);

Stock stock = response.getItem();
double ru = response.getRequestCharge();
```

### 2. Single-Partition Query — Account's Portfolio

```java
// Query within a single partition (efficient!)
SqlQuerySpec query = new SqlQuerySpec(
    "SELECT * FROM c WHERE c.accountId = @accountId",
    new SqlParameter("@accountId", "ACC-001"));

CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
// SDK automatically routes to the correct partition

CosmosPagedIterable<Account> results = accountsContainer.queryItems(
    query, options, Account.class);
```

### 3. Cross-Partition Query — Stocks by Sector

```java
// This query doesn't include the partition key (/ticker),
// so it fans out to all partitions — more expensive
SqlQuerySpec query = new SqlQuerySpec(
    "SELECT c.ticker, c.companyName, c.currentPrice, c.sector " +
    "FROM c WHERE c.sector = @sector",
    new SqlParameter("@sector", "Technology"));

// Cross-partition queries are allowed but have higher RU cost
CosmosPagedIterable<Stock> results = stocksContainer.queryItems(
    query, new CosmosQueryRequestOptions(), Stock.class);
```

### 4. Accounts with Specific Stock in Portfolio

```java
// Query accounts that hold a specific stock (uses ARRAY_CONTAINS on embedded array)
SqlQuerySpec query = new SqlQuerySpec(
    "SELECT c.accountId, c.ownerName, c.accountType, h.ticker, h.shares, h.marketValue " +
    "FROM c JOIN h IN c.portfolio " +
    "WHERE h.ticker = @ticker",
    new SqlParameter("@ticker", "NVDA"));
```

> **Relational parallel:** This replaces a `JOIN accounts a ON h.account_id = a.id`  
> between separate tables. In Cosmos DB, the portfolio is embedded, so  
> `JOIN h IN c.portfolio` iterates the embedded array within the same document.

### 5. Pagination with Continuation Tokens

```java
// Set small page size to demonstrate pagination
CosmosQueryRequestOptions options = new CosmosQueryRequestOptions();
options.setMaxItemCount(5);    // Only 5 items per page

String continuationToken = null;
int pageNum = 0;

do {
    CosmosPagedIterable<Stock> results;
    if (continuationToken == null) {
        results = stocksContainer.queryItems(querySpec, options, Stock.class);
    } else {
        // BEST PRACTICE: Pass null for first page, token for subsequent pages
        // Never pass empty string ("") — guard against empty tokens
        results = stocksContainer.queryItems(querySpec, options, Stock.class);
    }

    // Process the current page
    for (FeedResponse<Stock> page : results.iterableByPage(continuationToken, 5)) {
        pageNum++;
        System.out.printf("--- Page %d (RU: %.2f) ---%n",
            pageNum, page.getRequestCharge());

        for (Stock stock : page.getResults()) {
            System.out.printf("  %s - %s - $%.2f%n",
                stock.getTicker(), stock.getCompanyName(), stock.getCurrentPrice());
        }

        continuationToken = page.getContinuationToken();
    }
} while (continuationToken != null);
```

---

## Exercise: Run the Query Demo

```powershell
mvn compile exec:java -Dexec.mainClass="com.cosmoslab.lab05.QueryDemo"
```

### Expected output:

```
=== Lab 5: Point Reads & Query Options ===

--- 1. Point Read: Stock AAPL ---
Stock: AAPL - Apple Inc. - $189.84
RU cost: 1.00

--- 2. Single-Partition Query: Account ACC-001 ---
Account: ACC-001 - James Mitchell (Aggressive)
  Portfolio: 5 holdings
  AAPL  | 500 shares | $94,920.00
  NVDA  | 200 shares | $175,670.00
  MSFT  | 300 shares | $127,566.00
  ...
RU cost: 2.89

--- 3. Cross-Partition Query: Technology stocks ---
Found 25 Technology stocks:
  AAPL - Apple Inc. - $189.84
  MSFT - Microsoft Corporation - $425.22
  ...
RU cost: 3.45 (cross-partition fan-out)

--- 4. Accounts holding NVDA ---
Found 8 accounts with NVDA:
  ACC-001 James Mitchell - 200 shares ($175,670.00)
  ACC-003 David Park - 500 shares ($439,175.00)
  ...
RU cost: 28.45 (cross-partition, JOIN on embedded array)

--- 5. Pagination Demo (5 items per page) ---
--- Page 1 (RU: 2.90) ---
  AAPL - Apple Inc. - $189.84
  MSFT - Microsoft Corporation - $425.22
  GOOGL - Alphabet Inc. - $175.98
  AMZN - Amazon.com Inc. - $186.50
  NVDA - NVIDIA Corporation - $878.35
--- Page 2 (RU: 2.85) ---
  META - Meta Platforms Inc. - $505.75
  ...
(20 pages total)
```

**Observe the RU costs:**
- Point read: ~1 RU
- Single-partition query: ~2-3 RU
- Cross-partition query: ~3-30 RU (scales with partition count and data)
- Cross-partition with JOIN: highest cost

---

## ✅ Checkpoint

- [ ] You understand when to use point read vs. query
- [ ] You always use parameterized queries (never string concatenation)
- [ ] You see the RU difference between single-partition and cross-partition queries
- [ ] You understand pagination with continuation tokens (not OFFSET/LIMIT)
- [ ] You can query embedded arrays using JOIN syntax

---

**Next:** [Lab 6 — SDK Diagnostics & Logging](lab-06-diagnostics.md)
