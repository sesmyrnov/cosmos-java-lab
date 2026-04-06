# Lab 3: Create Containers & Bulk Load Data

| Duration | ~20 minutes |
|----------|------------|
| **Goal** | Create `stocks` and `accounts` containers with proper partition keys, then bulk-load sample data from JSON files |

---

## Key Concepts

### Containers and Partition Keys

| Relational Concept | Cosmos DB Equivalent | Notes |
|-------------------|---------------------|-------|
| Table | Container | Schema-free — each document can differ |
| Primary Key | `id` field | Unique within a logical partition |
| Table partition | Partition Key | Determines data distribution |
| Clustered Index | Automatic indexing | All properties indexed by default |

### Choosing a Partition Key (Critical Decision!)

The partition key is the **most important design decision** in Cosmos DB. It determines:
- How data is **distributed** across physical partitions
- Optimize for workload profile ( Writes vs Reads)
- Optimize for Access Patterns: Which queries are **efficient** (single-partition) vs. **expensive** (cross-partition)
- Logical Partition key can not exceed the **20 GB logical partition limit**. If there is a risk for that - explore using Hierarchical PK or Syntethic PK option.

**Rules of thumb:**
1. **High cardinality** — thousands to millions of unique values
2. **Even distribution** — avoid hot partitions (one key getting all writes)
3. **Aligned with queries** — most queries should filter on partition key
4. **Immutable** — partition keys cannot be changed after creation

For our containers:

| Container | Partition Key | Why |
|-----------|--------------|-----|
| `stocks` | `/ticker` | Each stock is unique, all queries by ticker |
| `accounts` | `/accountId` | Each account is unique, portfolio embedded |

### Bulk Load — Throughput Mode

The Java SDK supports **bulk execution** which:
- Batches operations to minimize round-trips
- Uses parallel connections for maximum throughput
- Respects rate limiting (429) with automatic retries
- Is ~10-50x faster than individual inserts

---

## Code Walkthrough

Open [src/main/java/com/cosmoslab/lab03/BulkLoadDemo.java](../src/main/java/com/cosmoslab/lab03/BulkLoadDemo.java)

### Part 1: Creating Containers

```java
// Create container with partition key and throughput
CosmosContainerProperties containerProps =
    new CosmosContainerProperties(containerName, partitionKeyPath);

// For cloud (Serverless): throughput is automatic, just create
database.createContainerIfNotExists(containerProps);

// For provisioned: set throughput
// database.createContainerIfNotExists(containerProps, 
//     ThroughputProperties.createAutoscaleMaxThroughput(1000));
```

**Why these partition keys?**

```
stocks container:    /ticker    → "AAPL", "MSFT", "GOOGL" (100 unique values)
accounts container:  /accountId → "ACC-001", "ACC-002", ... (50 unique values)
```

- **Stocks:** Each ticker is a unique partition. Point reads by ticker are 1 RU.
- **Accounts:** Each account has its portfolio embedded (denormalized). All account data in one partition.

> **Relational comparison:** Instead of `accounts`, `holdings`, and `stocks` tables with JOINs,  
> we embed the portfolio inside the account document. This eliminates JOINs and makes reads fast.

### Part 2: Loading JSON Data

```java
// Read JSON file and deserialize to List<Stock>
ObjectMapper mapper = new ObjectMapper();
List<Stock> stocks = mapper.readValue(
    new File("data/stocks.json"),
    new TypeReference<List<Stock>>() {});
```

### Part 3: Bulk Insert with CosmosClient

The bulk insert pattern creates items using the SDK's built-in bulk executor:

```java
// Bulk create items — uses parallel TCP connections
for (Stock stock : stocks) {
    CosmosItemResponse<Stock> response = container.createItem(
        stock, new PartitionKey(stock.getTicker()), new CosmosItemRequestOptions());
    totalRU += response.getRequestCharge();
    count++;
}
```

> **Note:** For very large datasets (10K+ items), use `CosmosBulkOperations` with the  
> async client for optimal throughput. For our 150 documents, sequential inserts are fine.

---

## Data Model Review

### Stock Document (in `stocks` container)

```json
{
    "id": "STK-001",
    "ticker": "AAPL",
    "companyName": "Apple Inc.",
    "sector": "Technology",
    "industry": "Consumer Electronics",
    "exchange": "NASDAQ",
    "currentPrice": 189.84,
    "currency": "USD",
    "marketCap": 2950000000000,
    "peRatio": 31.2,
    "dividendYield": 0.52,
    "week52High": 199.62,
    "week52Low": 143.90,
    "updTimestamp": "2026-04-01T09:30:00Z",
    "type": "stock"
}
```

### Account Document (in `accounts` container) — with embedded portfolio

```json
{
    "id": "ACC-001",
    "accountId": "ACC-001",
    "accountName": "Wellington Growth Fund",
    "accountType": "Individual",
    "ownerName": "James Mitchell",
    "riskProfile": "Aggressive",
    "totalValue": 1245800.00,
    "portfolio": [
        {
            "ticker": "AAPL",
            "companyName": "Apple Inc.",
            "shares": 500,
            "avgCostBasis": 155.20,
            "currentPrice": 189.84,
            "marketValue": 94920.00,
            "gainLoss": 17320.00,
            "gainLossPercent": 22.30
        }
        // ... more holdings
    ],
    "type": "account"
}
```

> **Design Pattern:** The portfolio is **embedded** in the account document.  
> In a relational DB, this would be a separate `holdings` table with a foreign key.  
> Embedding works because portfolio data is always read with the account and  
> the total document size stays well under the 2 MB limit.

---

## Exercise: Run the Bulk Load

```powershell
mvn compile exec:java -Dexec.mainClass="com.cosmoslab.lab03.BulkLoadDemo"
```

### Expected output:

```
=== Lab 3: Create Containers & Bulk Load ===

Creating container: stocks (partition key: /ticker)
Container 'stocks' ready.

Creating container: accounts (partition key: /accountId)
Container 'accounts' ready.

Loading stocks from data/stocks.json...
Loaded 100 stocks. Total RU: 682.40 (avg 6.82 RU/item)

Loading accounts from data/accounts.json...
Loaded 50 accounts. Total RU: 1245.80 (avg 24.92 RU/item)

=== Bulk Load Complete ===
Total documents: 150
Total RU consumed: 1928.20
```

Notice:
- Stock documents (~0.5 KB each) cost ~6-7 RU per insert
- Account documents (~2-4 KB each, with embedded portfolio) cost ~20-30 RU per insert
- **RU cost scales with document size** — larger documents = more RU

---

## Verify in Data Explorer

1. Open Azure Portal Data Explorer (or emulator at `https://localhost:8081/_explorer/`)
2. Expand your database → `stocks` container → Items
3. Browse a few documents — note the system properties (`_rid`, `_self`, `_etag`, `_ts`)
4. Expand `accounts` container → browse documents with embedded portfolios

---

## Browse & Query Data with VS Code Cosmos DB Extension

Now that data is loaded, let's explore it using the **Azure Databases** extension in VS Code — no need to leave your editor.

### Connect to Your Cosmos DB Account

1. Click the **Azure** icon in the VS Code Activity Bar (left sidebar)
2. Expand **Cosmos DB** under the Resources section
3. If you don't see your account:
   - For **cloud accounts**: Click **Sign in to Azure** and authenticate. Your Cosmos DB account will appear under your subscription.
   - For the **emulator**: Right-click **Cosmos DB** → **Attach Emulator**. The local emulator appears as `localhost:8081`.

### Browse Documents in VS Code

1. Expand your Cosmos DB account → your database (`workshop_<YOUR_USER_ID>`) → `stocks` container → **Documents**
2. Click on any document to view its JSON — it opens in a VS Code editor tab
3. Notice the system-generated properties (`_rid`, `_self`, `_etag`, `_ts`) alongside your data
4. Expand the `accounts` container → **Documents** → open an account document
5. Scroll to the `portfolio` array — you can see the embedded holdings inline

> **Tip:** You can edit documents directly in VS Code and save them (`Ctrl+S`) — the extension writes changes back to Cosmos DB.

### Run Queries in VS Code

1. Right-click on the `stocks` container → **New Query**
2. A query editor opens with a default `SELECT * FROM c`. Run it with the **Execute Query** button (▶) or `Ctrl+Shift+Q`.
3. Try these queries:

**Query 1 — Find Technology stocks:**

```sql
SELECT c.ticker, c.companyName, c.currentPrice
FROM c
WHERE c.sector = "Technology"
ORDER BY c.currentPrice DESC
```

**Query 2 — Stocks with high dividend yield:**

```sql
SELECT c.ticker, c.companyName, c.dividendYield
FROM c
WHERE c.dividendYield > 2.0
```

**Query 3 — Account portfolio query (on `accounts` container):**

Right-click `accounts` → **New Query**:

```sql
SELECT c.accountName, c.totalValue, ARRAY_LENGTH(c.portfolio) AS holdingsCount
FROM c
WHERE c.totalValue > 1000000
```

4. Review the **Query Stats** in the results pane — it shows RU charge, document count, and execution time

### Browse Data in Azure Portal Data Explorer

For a richer query experience with full query stats:

1. Open the [Azure Portal](https://portal.azure.com) → navigate to your Cosmos DB account
2. Click **Data Explorer** in the left menu
3. Expand your database → `stocks` container → **Items** to browse documents
4. Click **New SQL Query** to open the query editor
5. Run the same queries above — the Portal shows **Request Charge (RU)**, **Index Lookup Time**, and **Query Engine Execution Time** in the results header
6. For the emulator, open `https://localhost:8081/_explorer/index.html` — the Data Explorer is built into the emulator UI

### Try the Cosmos DB Agent Kit

If you installed the **Cosmos DB Agent Kit** extension, try asking it about your data:

1. Open GitHub Copilot Chat (`Ctrl+Shift+I`)
2. Type: `@cosmosdb Show me the top 5 most expensive stocks`
3. The agent generates and explains the query — you can run it directly from the chat response

---

## ✅ Checkpoint

- [ ] `stocks` container created with partition key `/ticker`
- [ ] `accounts` container created with partition key `/accountId`
- [ ] 100 stock documents loaded
- [ ] 50 account documents loaded
- [ ] You can browse documents in the VS Code Cosmos DB extension
- [ ] You ran at least one query from VS Code and viewed RU cost
- [ ] You can browse documents in Data Explorer (Portal or emulator)
- [ ] You understand why portfolio is embedded (not in a separate container)

---

**Next:** [Lab 4 — CRUD Operations](lab-04-crud.md)
