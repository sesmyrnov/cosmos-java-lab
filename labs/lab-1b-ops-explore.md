# Lab 1B (Optional): Hands-On Cosmos DB Exploration — No Code Required

| Duration | ~20 minutes |
|----------|------------|
| **Goal** | Create containers, load data, run queries, and observe metrics using only VS Code and Azure Portal — no Java code |
| **Audience** | Operations, QA, data analysts, or anyone who wants to explore Cosmos DB through UI tools |

> **This lab is optional.** Developers following the main workshop track can skip to  
> [Lab 2 — Connectivity & Auth](lab-02-connectivity.md). If you complete this lab,  
> you still need to run Lab 3 later to create containers/data via Java code.

---

## What You'll Do

1. Create `stocks` and `accounts` containers manually (Portal + Azure CLI)
2. Import sample JSON data using the VS Code Cosmos DB extension
3. Run point reads and queries in VS Code's Query Editor
4. Observe RU cost, execution time, and index usage metrics

---

## Part 1: Create Containers

You need two containers in your `workshop_<YOUR_USER_ID>` database:

| Container | Partition Key | Description |
|-----------|--------------|-------------|
| `stocks` | `/ticker` | 100 stock/security documents |
| `accounts` | `/accountId` | 50 investment account documents with embedded portfolios |

### Option A — Create via Azure Portal

1. Open the [Azure Portal](https://portal.azure.com) → navigate to your Cosmos DB account
2. Click **Data Explorer** in the left menu
3. Click **New Container**

**Create the `stocks` container:**

| Field | Value |
|-------|-------|
| Database id | Select existing: `workshop_<YOUR_USER_ID>` |
| Container id | `stocks` |
| Partition key | `/ticker` |
| Container throughput | Manual — 400 RU/s (or leave default for Serverless) |

4. Click **OK**

**Create the `accounts` container:**

5. Click **New Container** again

| Field | Value |
|-------|-------|
| Database id | Select existing: `workshop_<YOUR_USER_ID>` |
| Container id | `accounts` |
| Partition key | `/accountId` |
| Container throughput | Manual — 400 RU/s (or leave default for Serverless) |

6. Click **OK**

> **For the emulator:** Open `https://localhost:8081/_explorer/index.html` and follow the same steps.

### Option B — Create via Azure CLI

```powershell
# Set your variables
$ACCOUNT_NAME = "<your-cosmos-account>"
$RESOURCE_GROUP = "<your-resource-group>"
$DATABASE_NAME = "workshop_<YOUR_USER_ID>"

# Create the stocks container
az cosmosdb sql container create `
    --account-name $ACCOUNT_NAME `
    --resource-group $RESOURCE_GROUP `
    --database-name $DATABASE_NAME `
    --name stocks `
    --partition-key-path "/ticker"

# Create the accounts container
az cosmosdb sql container create `
    --account-name $ACCOUNT_NAME `
    --resource-group $RESOURCE_GROUP `
    --database-name $DATABASE_NAME `
    --name accounts `
    --partition-key-path "/accountId"
```

**Verify:**

```powershell
# List containers in your database
az cosmosdb sql container list `
    --account-name $ACCOUNT_NAME `
    --resource-group $RESOURCE_GROUP `
    --database-name $DATABASE_NAME `
    --query "[].name" -o tsv
```

Expected output:

```
accounts
stocks
```

### Option C — Create via VS Code

1. Open VS Code → click the **Azure** icon in the Activity Bar
2. Expand **Cosmos DB** → your account → your database (`workshop_<YOUR_USER_ID>`)
3. Right-click on the database → **Create Container...**
4. Enter container name: `stocks`
5. Enter partition key: `/ticker`
6. Repeat for `accounts` with partition key `/accountId`

---

## Part 2: Import JSON Data via VS Code

The VS Code Azure Databases extension can import JSON documents directly into containers.

### Import stocks data

1. In VS Code, open the **Explorer** sidebar and navigate to `data/stocks.json` in the project
2. **Right-click** on `stocks.json` → select **Import into Cosmos DB...**
3. When prompted, select your Cosmos DB account → database (`workshop_<YOUR_USER_ID>`) → container (`stocks`)
4. The extension will import all 100 stock documents. Watch the notification area (bottom-right) for progress.

> **Alternative:** In the Azure sidebar, expand your database → right-click on the `stocks` container → **Import Documents...** → browse to `data/stocks.json`.

### Import accounts data

1. Right-click on `data/accounts.json` → **Import into Cosmos DB...**
2. Select your account → database → `accounts` container
3. Wait for the 50 account documents to import

### Verify document counts

After import, expand each container in the Azure sidebar tree:

- `stocks` → **Documents** — you should see documents listed (click any to view)
- `accounts` → **Documents** — click an account document and scroll to the `portfolio` array to see embedded holdings

> **Tip:** The document viewer shows both your data properties and Cosmos DB system properties  
> (`_rid`, `_self`, `_etag`, `_ts`). The `_etag` is used for optimistic concurrency (covered in Lab 4).

---

## Part 3: Point Reads and Queries in VS Code

### 3.1 — Browse a single document (Point Read equivalent)

1. In the Azure sidebar, expand `stocks` container → **Documents**
2. Click on any document — it opens in a VS Code editor tab
3. This is the equivalent of a **point read** — the cheapest operation in Cosmos DB (~1 RU)
4. Note the document structure: `id`, `ticker`, `companyName`, `sector`, `currentPrice`, etc.

### 3.2 — Open the Query Editor

1. Right-click on the `stocks` container → **New Query**
2. A query editor tab opens, pre-filled with `SELECT * FROM c`
3. Click the **Execute Query** button (▶) or press `Ctrl+Shift+Q`
4. Results appear below in Table/JSON/Tree view

### 3.3 — Run targeted queries

Replace the query with each of the following. After each execution, review the **Query Stats** panel.

**Query 1 — Single-partition point read (cheapest):**

```sql
SELECT * FROM c WHERE c.ticker = "AAPL"
```

> This targets a single partition. Check the RU charge — should be very low (~3 RU).

**Query 2 — Single-partition with projection:**

```sql
SELECT c.ticker, c.companyName, c.currentPrice, c.marketCap
FROM c
WHERE c.ticker = "MSFT"
```

> Projection (selecting specific fields) reduces the response size and can lower RU cost.

**Query 3 — Cross-partition query by sector (more expensive):**

```sql
SELECT c.ticker, c.companyName, c.currentPrice
FROM c
WHERE c.sector = "Technology"
ORDER BY c.currentPrice DESC
```

> This scans multiple partitions. Compare the RU charge vs. Query 1. Cross-partition queries  
> cost more because the SDK must fan out to every partition.

**Query 4 — Aggregation query:**

```sql
SELECT
    c.sector,
    COUNT(1) AS stockCount,
    AVG(c.currentPrice) AS avgPrice,
    MIN(c.currentPrice) AS minPrice,
    MAX(c.currentPrice) AS maxPrice
FROM c
GROUP BY c.sector
```

> Aggregations are cross-partition. Note the higher RU cost.

**Query 5 — Portfolio query (on `accounts` container):**

Right-click `accounts` → **New Query**:

```sql
SELECT
    c.accountName,
    c.ownerName,
    c.accountType,
    c.totalValue,
    ARRAY_LENGTH(c.portfolio) AS holdingCount
FROM c
WHERE c.totalValue > 2000000
ORDER BY c.totalValue DESC
```

**Query 6 — Embedded array query with JOIN:**

```sql
SELECT
    c.accountName,
    h.ticker,
    h.shares,
    h.marketValue,
    h.gainLossPercent
FROM c
JOIN h IN c.portfolio
WHERE h.ticker = "AAPL"
```

>> The `JOIN` here is *intra-document* — it "unwinds" the embedded `portfolio` array.  
> This is NOT a cross-container join. Cosmos DB does not support traditional table joins.

### 3.4 — Index Metrics and Composite Index Recommendations

Queries with **range filters** (`>`, `<`, `>=`, `<=`) combined with **ORDER BY** on a different property can benefit from **composite indexes**. Cosmos DB indexes every property by default (single-property range indexes), but multi-property filter+sort patterns may need a composite index for optimal performance.

Let's use the VS Code Index Advisor and the Portal to evaluate this.

#### Step 1: Run a range filter + ORDER BY query

Go back to the `stocks` container query editor and run:

```sql
SELECT c.ticker, c.companyName, c.currentPrice, c.dividendYield
FROM c
WHERE c.dividendYield > 1.5
ORDER BY c.currentPrice DESC
```

This query **filters** on `dividendYield` (range) and **sorts** on `currentPrice` (descending) — two different properties.

#### Step 2: Check the Index Advisor in VS Code

After execution, look at the **Query Insights** section in the results pane. The **Index Advisor** may show a recommendation like:

```
Potential Composite Index:
  ORDER BY c.currentPrice DESC would benefit from a composite index:
  [{"path": "/dividendYield", "order": "ascending"}, {"path": "/currentPrice", "order": "descending"}]
```

> **What this means:** Without a composite index, the engine retrieves all documents matching `dividendYield > 1.5`  
> and then sorts them in memory. With a composite index, the data is pre-sorted — which reduces RU cost and latency.

#### Step 3: Run another range + ORDER BY query

Try this on the `accounts` container:

```sql
SELECT c.accountName, c.riskProfile, c.totalValue
FROM c
WHERE c.totalValue > 500000
ORDER BY c.totalValue DESC
```

Now try a query where the filter and sort are on **the same property**:

```sql
SELECT c.accountName, c.totalValue
FROM c
WHERE c.totalValue > 1000000
ORDER BY c.totalValue DESC
```

> When the filter and ORDER BY use the **same property**, the default single-property range index  
> handles it efficiently — no composite index needed. The Index Advisor should show no recommendations.

#### Step 4: Check Index Metrics in Azure Portal

For a deeper view of index utilization:

1. Open the [Azure Portal](https://portal.azure.com) → your Cosmos DB account → **Data Explorer**
2. Select `stocks` container → **New SQL Query**
3. Paste the range filter query from Step 1 and click **Execute**
4. In the results header, click **Query Stats** and look for:
   - **Index Lookup Time** — time spent traversing the index
   - **Index Utilization** — whether the query fully used the index or fell back to a scan
   - **Retrieved Document Count** vs **Output Document Count** — if these differ significantly, the index isn't filtering efficiently

5. To view the current indexing policy, expand the container in Data Explorer → click **Scale & Settings** → scroll to **Indexing Policy**. The default policy looks like:

```json
{
  "indexingMode": "consistent",
  "automatic": true,
  "includedPaths": [{ "path": "/*" }],
  "excludedPaths": [{ "path": "/\"_etag\"/?" }],
  "compositeIndexes": []
}
```

> Note: `compositeIndexes` is empty by default — all indexes are single-property.

#### Step 5: Add a composite index (optional — observe the improvement)

If you want to see the improvement, add a composite index via the Portal:

1. In **Scale & Settings** → **Indexing Policy**, add a composite index:

```json
{
  "indexingMode": "consistent",
  "automatic": true,
  "includedPaths": [{ "path": "/*" }],
  "excludedPaths": [{ "path": "/\"_etag\"/?" }],
  "compositeIndexes": [
    [
      { "path": "/dividendYield", "order": "ascending" },
      { "path": "/currentPrice", "order": "descending" }
    ]
  ]
}
```

2. Click **Save** — the index builds in the background (takes seconds for small datasets)
3. Re-run the same query from Step 1
4. Compare the **RU charge** and **Index Lookup Time** — both should be lower

Or add via Azure CLI:

```powershell
az cosmosdb sql container update `
    --account-name $ACCOUNT_NAME `
    --resource-group $RESOURCE_GROUP `
    --database-name $DATABASE_NAME `
    --name stocks `
    --idx '{\"indexingMode\":\"consistent\",\"automatic\":true,\"includedPaths\":[{\"path\":\"/*\"}],\"excludedPaths\":[{\"path\":\"/\\\"_etag\\\"/?\"}],\"compositeIndexes\":[[{\"path\":\"/dividendYield\",\"order\":\"ascending\"},{\"path\":\"/currentPrice\",\"order\":\"descending\"}]]}'
```

#### When do you need composite indexes?

| Query Pattern | Default Index Sufficient? | Composite Index Needed? |
|--------------|--------------------------|------------------------|
| `WHERE a = X` | ✅ Yes | No |
| `WHERE a = X ORDER BY a` | ✅ Yes | No |
| `WHERE a > X ORDER BY a` | ✅ Yes (same property) | No |
| `WHERE a = X ORDER BY b` | ⚠️ Works but suboptimal | **Yes** — reduces RU |
| `WHERE a > X ORDER BY b DESC` | ⚠️ Works but scans | **Yes** — eliminates scan |
| `ORDER BY a ASC, b DESC` | ❌ Fails without it | **Required** |
| `WHERE a > X AND b < Y` | ⚠️ Partial index use | **Yes** — reduces RU |

> **Rule of thumb:** If your query filters on property A and sorts on property B (or sorts on  
> multiple properties in mixed order), add a composite index. The VS Code Index Advisor will  
> tell you exactly which one to create.

---

## Part 4: Observe Metrics and Query Insights

### 4.1 — Query Stats in VS Code

After running any query in the VS Code Query Editor, review the **Query Insights** section in the results pane:

| Metric | What It Tells You |
|--------|------------------|
| **Request Charge (RU)** | Total cost of the query in Request Units |
| **Retrieved Document Count** | Number of documents the engine evaluated |
| **Output Document Count** | Number of documents returned to you |
| **Execution Time** | Server-side processing time |
| **Index Lookup Time** | Time spent finding matching documents via indexes |
| **Document Load Time** | Time spent reading document content from storage |
| **Index Advisor** | Suggestions for improving index performance |

> **Key insight:** If Retrieved Document Count >> Output Document Count, your query is scanning  
> more documents than necessary. Consider adding a filter on the partition key or adjusting indexes.

### 4.2 — Compare RU costs

Run these three queries on the `stocks` container and record the RU charge for each:

| Query | Type | Expected RU |
|-------|------|-------------|
| `SELECT * FROM c WHERE c.ticker = "AAPL"` | Single-partition, point lookup | ~3 RU |
| `SELECT * FROM c WHERE c.sector = "Technology"` | Cross-partition, filtered | ~10-20 RU |
| `SELECT * FROM c` | Full scan | ~30-50 RU |

> **Takeaway:** Always query with the partition key when possible. It's the difference  
> between scanning one partition vs. all partitions.

### 4.3 — Metrics in Azure Portal

For more detailed metrics, use the Portal:

1. Navigate to your Cosmos DB account in the [Azure Portal](https://portal.azure.com)
2. Click **Metrics** in the left menu under **Monitoring**
3. Set the following:
   - **Metric**: `Total Request Units`
   - **Aggregation**: `Sum`
   - **Time range**: Last 30 minutes
4. Run a few queries from VS Code, then refresh the Portal metrics chart
5. You should see RU consumption spikes corresponding to your queries

**Additional useful metrics to explore:**

| Metric | What to look for |
|--------|-----------------|
| `Total Request Units` | Overall RU consumption over time |
| `Total Requests` | Request count — broken down by status code |
| `Normalized RU Consumption` | How close to RU limit (relevant for provisioned throughput) |
| `Document Count` | Total documents per container |
| `Data Storage` | Storage consumed per container |

> **Filter by container:** In the metrics chart, click **Add filter** → **CollectionName** →  
> select `stocks` or `accounts` to see per-container metrics.

### 4.4 — Cosmos DB Insights (Azure Portal)

For a richer dashboard:

1. In your Cosmos DB account, click **Insights** in the left menu
2. Explore the **Overview** tab — it shows throughput, requests, storage, availability, and latency
3. Click the **Throughput** tab to see per-container RU utilization
4. The **Requests** tab shows success/failure breakdown by status code (200, 304, 404, 429)

---

## Part 5: Experiment on Your Own

Try writing your own queries. Here are some ideas:

1. **Find stocks with P/E ratio under 15:** `SELECT c.ticker, c.companyName, c.peRatio FROM c WHERE c.peRatio < 15`
2. **Find the largest accounts by total value:** `SELECT TOP 5 c.accountName, c.totalValue FROM c ORDER BY c.totalValue DESC`
3. **Find accounts holding more than 10 stocks:** `SELECT c.accountName, ARRAY_LENGTH(c.portfolio) AS count FROM c WHERE ARRAY_LENGTH(c.portfolio) > 10`
4. **Calculate total market value of all AAPL holdings across accounts:**
   ```sql
   SELECT SUM(h.marketValue) AS totalAAPLValue
   FROM c JOIN h IN c.portfolio
   WHERE h.ticker = "AAPL"
   ```

---

## ✅ Checkpoint

- [ ] Created `stocks` and `accounts` containers (Portal, CLI, or VS Code)
- [ ] Imported `stocks.json` (100 documents) and `accounts.json` (50 documents) via VS Code
- [ ] Ran at least 3 different queries in the VS Code Query Editor
- [ ] Reviewed Query Insights: RU charge, document counts, execution time
- [ ] Compared RU costs: single-partition vs. cross-partition vs. full scan
- [ ] Explored Metrics or Insights in the Azure Portal
- [ ] You understand: partition key filtering → lower RU cost

---

**Next:** [Lab 2 — Connectivity, Client Options & Authentication](lab-02-connectivity.md)  
**Or continue the main track:** [Lab 1 Checkpoint](lab-01-setup.md#-checkpoint)
