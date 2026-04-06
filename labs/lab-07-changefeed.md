# Lab 7: Cosmos DB Change Feed

| Duration | ~15 minutes |
|----------|------------|
| **Goal** | Use Change Feed to build a real-time pipeline: simulate stock price changes → Change Feed Processor → update stocks container |

---

## Key Concepts

### What is Change Feed?

Change Feed is a **persistent, ordered log of changes** to items in a container. It:

- Captures all inserts and updates (not deletes, unless soft-delete is used)
- Provides changes in **modification order** within each logical partition
- Supports **at-least-once** delivery guarantee
- Is the Cosmos DB equivalent of database triggers, CDC (Change Data Capture), or event sourcing

### Architecture for This Lab

```
┌────────────────────┐          ┌──────────────────┐          ┌──────────────────┐
│  Price Simulator   │ inserts  │ stock_time_series │  Change  │  Change Feed     │
│  (StockPrice       │ ───────► │ container         │  Feed    │  Processor       │
│   Simulator)       │          │ (partition: /ticker)│ ───────► │  (reads changes) │
└────────────────────┘          └──────────────────┘          └────────┬─────────┘
                                                                       │ updates
                                                                       ▼
                                                              ┌──────────────────┐
                                                              │ stocks container  │
                                                              │ (updated price &  │
                                                              │  updTimestamp)     │
                                                              └──────────────────┘
```

1. **StockPriceSimulator** — Inserts immutable price tick documents into `stock_time_series`
2. **Change Feed Processor** — Reads changes and updates `currentPrice` and `updTimestamp` in the `stocks` container
3. **Leases container** — Tracks processor progress (checkpoints)

### Change Feed vs. Relational Triggers

| Relational (Triggers) | Cosmos DB (Change Feed) |
|----------------------|------------------------|
| Synchronous — blocks the write | Asynchronous — writes succeed independently |
| Runs inside the DB engine | Runs in your application (any language) |
| Single server | Distributed — tracks partitions independently |
| Difficult to scale | Horizontal scaling with multiple processor instances |
| `AFTER INSERT` trigger | Change Feed Processor listens for changes |

### Lease Container

The processor uses a **leases container** to track:
- Which changes have been processed (checkpoint)
- Which processor instance owns which partition
- Progress recovery after restarts

> Think of it like a Kafka consumer group's offset tracking.

---

## Code Walkthrough

### Part 1: Stock Price Simulator

Open [src/main/java/com/cosmoslab/lab07/StockPriceSimulator.java](../src/main/java/com/cosmoslab/lab07/StockPriceSimulator.java)

```java
// Simulate price changes for selected stocks
String[] tickers = {"AAPL", "MSFT", "GOOGL", "NVDA", "JPM"};

for (int round = 0; round < 5; round++) {
    for (String ticker : tickers) {
        // Generate random price change (-2% to +2%)
        double previousPrice = getCurrentPrice(ticker);
        double change = previousPrice * (random.nextDouble() * 0.04 - 0.02);
        double newPrice = Math.round((previousPrice + change) * 100.0) / 100.0;

        // Create immutable time-series document
        StockTimeSeries tick = new StockTimeSeries(
            UUID.randomUUID().toString(),
            ticker, newPrice, previousPrice,
            random.nextLong(100000, 5000000),
            Instant.now().toString()
        );

        // Insert into stock_time_series container
        timeSeriesContainer.createItem(tick, new PartitionKey(ticker), options);
    }
    Thread.sleep(2000);  // Wait between rounds
}
```

### Part 2: Change Feed Processor

Open [src/main/java/com/cosmoslab/lab07/ChangeFeedProcessorApp.java](../src/main/java/com/cosmoslab/lab07/ChangeFeedProcessorApp.java)

```java
// Build the Change Feed Processor
ChangeFeedProcessor processor = new ChangeFeedProcessorBuilder()
    .hostName("workshop-processor-" + ProcessHandle.current().pid())
    .feedContainer(timeSeriesAsyncContainer)    // Source: stock_time_series
    .leaseContainer(leaseAsyncContainer)        // Checkpoint tracking
    .handleChanges((List<JsonNode> changes, ChangeFeedProcessorContext context) -> {
        for (JsonNode change : changes) {
            String ticker = change.get("ticker").asText();
            double newPrice = change.get("price").asDouble();
            String timestamp = change.get("timestamp").asText();

            // Update the stock document in the stocks container
            CosmosPatchOperations patchOps = CosmosPatchOperations.create()
                .set("/currentPrice", newPrice)
                .set("/updTimestamp", timestamp);

            // Use Patch — no read needed, atomic update
            stocksAsyncContainer.patchItem(
                getStockId(ticker),
                new PartitionKey(ticker),
                patchOps,
                new CosmosPatchItemRequestOptions(),
                JsonNode.class
            ).block();

            System.out.printf("[CFP] Updated %s: $%.2f at %s%n",
                ticker, newPrice, timestamp);
        }
    })
    .buildChangeFeedProcessor();

// Start the processor
processor.start().block();
```

---

## Exercise: Run the Change Feed Demo

This lab has two programs that run together:

### Step 1: Create the time-series and leases containers

The processor app creates them automatically, but let's start the **processor first**:

```powershell
# Terminal 1: Start the Change Feed Processor
mvn compile exec:java -Dexec.mainClass="com.cosmoslab.lab07.ChangeFeedProcessorApp"
```

It will output:
```
Change Feed Processor started. Waiting for changes...
(Press Enter to stop)
```

### Step 2: In a second terminal, run the price simulator

```powershell
# Terminal 2: Simulate stock price changes
mvn compile exec:java -Dexec.mainClass="com.cosmoslab.lab07.StockPriceSimulator"
```

### Expected output:

**Terminal 2 (Simulator):**
```
=== Stock Price Simulator ===
Round 1/5:
  [INSERT] AAPL: $189.84 -> $191.22 (tick at 2026-04-03T14:30:01Z)
  [INSERT] MSFT: $425.22 -> $423.88 (tick at 2026-04-03T14:30:01Z)
  [INSERT] GOOGL: $175.98 -> $177.42 (tick at 2026-04-03T14:30:01Z)
  ...
Round 2/5:
  [INSERT] AAPL: $191.22 -> $190.45 ...
```

**Terminal 1 (Processor):**
```
[CFP] Processing 5 changes...
[CFP] Updated AAPL: $191.22 at 2026-04-03T14:30:01Z
[CFP] Updated MSFT: $423.88 at 2026-04-03T14:30:01Z
[CFP] Updated GOOGL: $177.42 at 2026-04-03T14:30:01Z
...
```

### Step 3: Verify in Data Explorer

1. Check `stock_time_series` container — should have 25 tick documents (5 stocks x 5 rounds)
2. Check `stocks` container — AAPL, MSFT, GOOGL, NVDA, JPM should have updated `currentPrice` and `updTimestamp`

### Step 4: Stop the processor

Press Enter in Terminal 1 to gracefully stop the Change Feed Processor.

---

## How Change Feed Works Internally

1. **Partition-level tracking:** Each logical partition's changes are tracked independently
2. **Checkpoint model:** The processor stores a lease per partition in the leases container
3. **At-least-once:** If the processor crashes, it restarts from the last checkpoint
4. **Ordering:** Changes within a partition are in order; across partitions, order is not guaranteed
5. **Latency:** Near real-time — typically < 1 second for small workloads

---

## ✅ Checkpoint

- [ ] You understand the Change Feed architecture (source → processor → target)
- [ ] Price simulator inserts immutable time-series documents
- [ ] Change Feed Processor reads changes and updates stock prices
- [ ] You know the role of the leases container (checkpoint tracking)
- [ ] You understand at-least-once delivery semantics
- [ ] Stock documents in `stocks` container reflect the latest simulated prices

---

**Congratulations!** You've completed all 7 labs of the Cosmos DB Java Workshop.

**Return to:** [Workshop Overview](../README.md)
