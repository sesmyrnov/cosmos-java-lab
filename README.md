# Cosmos DB NoSQL — Java Developer Workshop

> **Duration:** ~2 hours  
> **Audience:** Java developers with relational database backgrounds  
> **Domain:** Financial Asset Management

---

## Overview

This hands-on workshop teaches Azure Cosmos DB NoSQL fundamentals through a financial services scenario. You'll work with stock market data and investment account portfolios — building, querying, and processing data using the Azure Cosmos DB Java SDK v4.

### What You'll Learn

| Topic | Relational Equivalent |
|-------|-----------------------|
| Partition keys & containers | Tables & indexes |
| Embedded documents | Denormalized joins |
| Point reads (O(1) by id + partition key) | `SELECT * FROM t WHERE id = @id` |
| Cross-partition queries | Full table scans |
| Change Feed | CDC / triggers |
| Request Units (RU) | Query cost |
| ETag concurrency | Optimistic locking |

---

## Prerequisites

- **Java 17+** and **Maven 3.8+**
- **Azure subscription** with Cosmos DB account — OR — [Azure Cosmos DB Emulator](https://learn.microsoft.com/en-us/azure/cosmos-db/local-emulator)
- IDE (VS Code with Java extensions, IntelliJ, etc.)
- Terminal / PowerShell

### Quick Start

```powershell
# 1. Clone/download this project
cd cosmos-java-lab

# 2. Configure your connection (edit the properties file)
notepad src\main\resources\application.properties

# 3. Build the project
mvn clean compile

# 4. Start with Lab 1
```

---

## Agenda

| # | Lab | Duration | Topic |
|---|-----|----------|-------|
| 1 | [Setup & Deployment](labs/lab-01-setup.md) | 15 min | Azure/Emulator setup, SSL certs, database creation |
| 1B | [Ops/QA Exploration (Optional)](labs/lab-1b-ops-explore.md) | 20 min | Create containers manually, import JSON via VS Code, queries & metrics |
| 2 | [Connectivity & Auth](labs/lab-02-connectivity.md) | 10 min | Singleton pattern, Direct vs Gateway, Key vs Entra ID |
| 3 | [Containers & Bulk Load](labs/lab-03-containers-bulkload.md) | 15 min | Partition keys, data modeling, loading 150 documents |
| 4 | [CRUD Operations](labs/lab-04-crud.md) | 20 min | Create, Read, Replace (ETag), Patch, Delete |
| 5 | [Queries & Pagination](labs/lab-05-queries.md) | 20 min | Point reads, single/cross-partition queries, JOINs, continuation tokens |
| 6 | [Diagnostics & Throttling](labs/lab-06-diagnostics.md) | 15 min | CosmosDiagnostics, RU tracking, 429 simulation |
| 7 | [Change Feed](labs/lab-07-changefeed.md) | 15 min | Real-time price pipeline with ChangeFeedProcessor |
| | **Buffer / Q&A** | 10 min | |

---

## Project Structure

```
cosmos-java-lab/
├── README.md                              ← You are here
├── pom.xml                                ← Maven dependencies (Cosmos SDK v4.65.0)
├── labs/
│   ├── lab-01-setup.md                    ← Setup instructions
│   ├── lab-1b-ops-explore.md              ← (Optional) Ops/QA: UI-based exploration
│   ├── lab-02-connectivity.md             ← Connectivity & auth
│   ├── lab-03-containers-bulkload.md      ← Container creation & data loading
│   ├── lab-04-crud.md                     ← CRUD operations
│   ├── lab-05-queries.md                  ← Queries & pagination
│   ├── lab-06-diagnostics.md              ← SDK diagnostics
│   └── lab-07-changefeed.md               ← Change Feed processor
├── data/
│   ├── stocks.json                        ← 100 stock records
│   └── accounts.json                      ← 50 investment account records
└── src/main/java/com/cosmoslab/
    ├── common/
    │   ├── Config.java                    ← Configuration loader
    │   └── CosmosClientFactory.java       ← Singleton client factory
    ├── models/
    │   ├── Stock.java                     ← Stock document model
    │   ├── Account.java                   ← Account with embedded portfolio
    │   ├── Holding.java                   ← Portfolio holding (embedded)
    │   └── StockTimeSeries.java           ← Time-series price tick
    ├── lab02/ConnectivityDemo.java
    ├── lab03/BulkLoadDemo.java
    ├── lab04/CrudOperationsApp.java
    ├── lab05/QueryDemo.java
    ├── lab06/DiagnosticsDemo.java
    └── lab07/
        ├── StockPriceSimulator.java       ← Simulates price changes
        └── ChangeFeedProcessorApp.java    ← Processes changes via Change Feed
```

---

## Data Model

### Stocks Container (partition key: `/ticker`)

```json
{
  "id": "STK-001",
  "ticker": "AAPL",
  "companyName": "Apple Inc.",
  "sector": "Technology",
  "currentPrice": 189.84,
  "marketCap": 2950000000000,
  ...
}
```

### Accounts Container (partition key: `/accountId`)

```json
{
  "id": "ACC-001",
  "accountId": "ACC-001",
  "accountName": "Meridian Growth Portfolio",
  "accountType": "Individual",
  "riskProfile": "Aggressive",
  "portfolio": [
    {
      "ticker": "AAPL",
      "shares": 150,
      "costBasis": 28500.00,
      "marketValue": 33225.00,
      "gainLoss": 4725.00
    }
  ],
  ...
}
```

> **Key design choice:** Portfolio holdings are *embedded* in the account document (not in a separate container). This mirrors the Cosmos DB best practice of modeling for read patterns — you retrieve the full account with one point read, no joins needed.

---

## Running Labs

Each lab has a corresponding Java class. Run with Maven:

```powershell
# Lab 2: Connectivity
mvn compile exec:java -Dexec.mainClass="com.cosmoslab.lab02.ConnectivityDemo"

# Lab 3: Bulk Load
mvn compile exec:java -Dexec.mainClass="com.cosmoslab.lab03.BulkLoadDemo"

# Lab 4: CRUD (interactive menu)
mvn compile exec:java -Dexec.mainClass="com.cosmoslab.lab04.CrudOperationsApp"

# Lab 5: Queries
mvn compile exec:java -Dexec.mainClass="com.cosmoslab.lab05.QueryDemo"

# Lab 6: Diagnostics
mvn compile exec:java -Dexec.mainClass="com.cosmoslab.lab06.DiagnosticsDemo"

# Lab 7: Change Feed (two terminals)
mvn compile exec:java -Dexec.mainClass="com.cosmoslab.lab07.ChangeFeedProcessorApp"
mvn compile exec:java -Dexec.mainClass="com.cosmoslab.lab07.StockPriceSimulator"
```

---

## Key Best Practices Demonstrated

| Practice | Lab |
|----------|-----|
| Singleton `CosmosClient` (one per app lifetime) | 2 |
| Direct mode for production, Gateway for emulator | 2 |
| Partition key = high cardinality field (`/ticker`, `/accountId`) | 3 |
| Embed related data (account → holdings) | 3, 5 |
| Point reads by `id` + partition key (cheapest: ~1 RU) | 4, 5 |
| Parameterized queries (never string-concatenate!) | 5 |
| Continuation token pagination (not OFFSET/LIMIT) | 5 |
| ETag optimistic concurrency for safe updates | 4 |
| Patch API for partial updates (no read-modify-write) | 4 |
| `CosmosDiagnostics` for per-request observability | 6 |
| Change Feed for event-driven architectures | 7 |

---

## Cleanup

After completing the workshop, delete your database to avoid charges:

```powershell
# Using Azure CLI
az cosmosdb sql database delete \
  --account-name <your-account> \
  --resource-group <your-rg> \
  --name workshop_<YOUR_USER_ID>
```

Or delete from the Azure Portal → Cosmos DB account → Data Explorer → right-click database → Delete.

---

## Resources

- [Azure Cosmos DB Java SDK v4 Documentation](https://learn.microsoft.com/en-us/azure/cosmos-db/nosql/sdk-java-v4)
- [Best Practices for Java SDK v4](https://learn.microsoft.com/en-us/azure/cosmos-db/nosql/best-practice-java)
- [Data Modeling in Cosmos DB](https://learn.microsoft.com/en-us/azure/cosmos-db/nosql/modeling-data)
- [Partition Key Best Practices](https://learn.microsoft.com/en-us/azure/cosmos-db/partitioning-overview)
- [Change Feed Processor](https://learn.microsoft.com/en-us/azure/cosmos-db/nosql/change-feed-processor)
