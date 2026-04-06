# Lab 6: SDK Diagnostics & Logging

| Duration | ~15 minutes |
|----------|------------|
| **Goal** | Capture RU/latency metrics via CosmosDiagnostics, simulate 429 throttling, and analyze diagnostic output |

---

## Key Concepts

### Why Diagnostics Matter

In relational databases, you analyze query plans and execution stats with `EXPLAIN` or SQL Profiler.  
In Cosmos DB, every operation returns **diagnostics** that tell you:

- **Request Charge (RU)** — how many Request Units it consumed
- **Latency** — client-side and server-side duration
- **Retries** — how many 429 retries occurred
- **Region** — which region served the request
- **Partition info** — which physical/logical partition was hit

### CosmosDiagnostics (Java SDK)

```java
CosmosItemResponse<Stock> response = container.readItem(id, pk, Stock.class);

// RU cost
double ru = response.getRequestCharge();

// Full diagnostics object
CosmosDiagnostics diagnostics = response.getDiagnostics();

// Client-side elapsed time (includes network + retries)
Duration elapsed = diagnostics.getDuration();

// Full diagnostic string (JSON-like output with all details)
String diagString = diagnostics.toString();
```

### 429 Throttling — Rate Limiting

When you exceed your provisioned RU/s (or burst capacity on serverless), Cosmos DB returns:

- **HTTP 429** — Too Many Requests
- **Retry-After** header — tells you when to retry

The Java SDK handles this **automatically** with configurable retry:

```java
ThrottlingRetryOptions retryOptions = new ThrottlingRetryOptions();
retryOptions.setMaxRetryAttemptsOnThrottledRequests(9);
retryOptions.setMaxRetryWaitTime(Duration.ofSeconds(30));
```

If all retries fail, a `CosmosException` with status 429 is thrown.

---

## Code Walkthrough

Open [src/main/java/com/cosmoslab/lab06/DiagnosticsDemo.java](../src/main/java/com/cosmoslab/lab06/DiagnosticsDemo.java)

### Part 1: Capturing Diagnostics on Normal Operations

```java
CosmosItemResponse<Stock> response = stocksContainer.readItem(
    "STK-001", new PartitionKey("AAPL"), Stock.class);

System.out.println("=== Operation Metrics ===");
System.out.printf("Request Charge: %.2f RU%n", response.getRequestCharge());
System.out.printf("Status Code: %d%n", response.getStatusCode());
System.out.printf("Client Latency: %d ms%n",
    response.getDiagnostics().getDuration().toMillis());

// Full diagnostic output
System.out.println("\n=== Full Diagnostics ===");
System.out.println(response.getDiagnostics().toString());
```

### Part 2: Diagnostics on Queries (aggregate across pages)

```java
double totalRU = 0;
long totalLatency = 0;

for (FeedResponse<Stock> page : results.iterableByPage()) {
    totalRU += page.getRequestCharge();
    totalLatency += page.getCosmosDiagnostics().getDuration().toMillis();
    
    // Each page has its own diagnostics
    System.out.println(page.getCosmosDiagnostics().toString());
}
```

### Part 3: Error Diagnostics

```java
try {
    container.readItem("nonexistent", new PartitionKey("none"), Stock.class);
} catch (CosmosException e) {
    System.out.printf("Error Status: %d%n", e.getStatusCode());
    System.out.printf("Error RU: %.2f%n", e.getRequestCharge());
    System.out.printf("Diagnostics: %s%n", e.getDiagnostics());
}
```

### Part 4: Simulate 429 Throttling

We simulate throttling by performing rapid bulk inserts (many small documents very fast).  
On a Serverless account, the 5,000 RU/s limit makes this easy to trigger:

```java
// Rapid-fire inserts to trigger 429
for (int i = 0; i < 500; i++) {
    Stock tempStock = createTempStock(i);
    container.createItem(tempStock, new PartitionKey(tempStock.getTicker()),
        new CosmosItemRequestOptions());
}
```

When a 429 occurs, the SDK retries automatically. We capture the diagnostics to see retry behavior.

---

## Understanding Diagnostic Output

The diagnostics string contains several key sections:

### 1. Summary Section
```json
{
    "userAgent": "azsdk-java-cosmos/4.65.0 ...",
    "activityId": "d4e5f6-...",
    "requestLatencyInMs": 3.45,
    "requestCharge": 6.82,
    "statusCode": 201,
    "resourceType": "Document"
}
```

### 2. Client-Side Metrics
- `requestLatencyInMs` — total client-side time
- `numberOfRetries` — how many 429 retries
- `retryAfterInMs` — time spent waiting on retry-after

### 3. Connection Info
- `connectionMode` — "DIRECT" or "GATEWAY"
- `endpoint` — which endpoint served the request
- `partitionKeyRangeId` — physical partition that handled it

### 4. Server-Side Metrics (on queries)
- `queryMetrics` — query execution statistics
- `indexUtilization` — which indexes were used
- `retrievedDocumentCount` — documents scanned vs. returned

---

## Exercise: Run the Diagnostics Demo

First, enable verbose logging for the Cosmos SDK. Edit `src/main/resources/logback.xml`:

```xml
<!-- Change from WARN to INFO for this lab -->
<logger name="com.azure.cosmos" level="INFO" />
```

Then run:

```powershell
mvn compile exec:java -Dexec.mainClass="com.cosmoslab.lab06.DiagnosticsDemo"
```

### Expected output sections:

```
=== Part 1: Point Read Diagnostics ===
Request Charge: 1.00 RU
Status Code: 200
Client Latency: 5 ms

=== Part 2: Query Diagnostics ===
Query: SELECT * FROM c WHERE c.sector = @sector
Total RU across pages: 3.45
Total client latency: 12 ms

=== Part 3: Error Diagnostics ===
Error Status: 404 (Not Found)
Error RU: 0.00

=== Part 4: 429 Throttling Simulation ===
Inserting 500 documents rapidly...
Operations completed: 500
Total RU: 3410.00
429 retries observed: 12
Max retry delay: 850 ms
```

### After the demo

Revert `logback.xml` to `WARN` level for subsequent labs:

```xml
<logger name="com.azure.cosmos" level="WARN" />
```

---

## ✅ Checkpoint

- [ ] You can capture RU and latency from every operation
- [ ] You understand the structure of CosmosDiagnostics output
- [ ] You saw 429 throttling in action with automatic SDK retries
- [ ] You know how to enable verbose SDK logging for troubleshooting

---

**Next:** [Lab 7 — Change Feed](lab-07-changefeed.md)
