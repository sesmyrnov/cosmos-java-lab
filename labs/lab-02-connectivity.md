# Lab 2: Connectivity, Client Options & Authentication

| Duration | ~15 minutes |
|----------|------------|
| **Goal** | Understand CosmosClient configuration, connection modes, and authentication |

---

## Key Concepts

### CosmosClient — The Singleton Pattern

In relational databases, you open and close connections per-request from a pool. In Cosmos DB:

- **Create `CosmosClient` ONCE** and reuse it for the entire application lifetime
- The client manages internal connection pooling and routing
- Creating a new client per-request causes **connection exhaustion, socket errors, and high latency**
- Register as a singleton in your DI container or use static lazy initialization

> **Think of it like:** `CosmosClient` ≈ your connection pool manager, not a single connection.

### Connection Modes

| Mode | Protocol | Best For | Latency |
|------|----------|----------|---------|
| **Direct** | TCP | Production cloud accounts | Lower (30-50% faster) |
| **Gateway** | HTTPS | Emulator, firewalled networks | Higher |

- **Direct mode** opens TCP connections directly to backend partition replicas
- **Gateway mode** routes through the Cosmos DB gateway (HTTPS)
- The emulator **requires Gateway mode** due to SSL certificate handling

### Authentication Options

| Method | When to Use |
|--------|-------------|
| **Key-based** | Development, quick start, legacy apps |
| **Entra ID (AAD)** | Production — no keys to rotate, RBAC, audit trail |

---

## Code Walkthrough

### 1. Configuration Loader (`Config.java`)

Open [src/main/java/com/cosmoslab/common/Config.java](../src/main/java/com/cosmoslab/common/Config.java)

This loads settings from `application.properties` and validates required values:

```java
// Key method — validates the setting is present and not a placeholder
private static String getRequired(String key) {
    String value = props.getProperty(key);
    if (value == null || value.isBlank() || value.contains("<")) {
        throw new RuntimeException(
                "Configuration '" + key + "' is missing or not set.");
    }
    return value;
}
```

**Relational parallel:** This is like your JDBC connection string configuration.

### 2. Client Factory (`CosmosClientFactory.java`)

Open [src/main/java/com/cosmoslab/common/CosmosClientFactory.java](../src/main/java/com/cosmoslab/common/CosmosClientFactory.java)

Walk through each configuration option:

```java
// BEST PRACTICE: Singleton pattern
private static CosmosClient instance;

public static synchronized CosmosClient getClient() {
    if (instance == null) {
        instance = createClient();
    }
    return instance;
}
```

```java
// BEST PRACTICE: Configure retry for 429 throttling
ThrottlingRetryOptions retryOptions = new ThrottlingRetryOptions();
retryOptions.setMaxRetryAttemptsOnThrottledRequests(9);
retryOptions.setMaxRetryWaitTime(Duration.ofSeconds(30));
```

```java
// BEST PRACTICE: Enable content response on writes
// Java SDK quirk — without this, createItem/upsertItem returns null from getItem()
builder.contentResponseOnWriteEnabled(true);
```

```java
// BEST PRACTICE: Choose connection mode based on environment
if ("GATEWAY".equalsIgnoreCase(connectionMode)) {
    builder.gatewayMode();    // For Emulator and all /general use-cases using HTTPS connection mode
} else {
    builder.directMode();     // For low/latency/ high throughput KV point operations - many parallel TCP connections
}
```

### 3. Entra ID Authentication

When using Entra ID (recommended for production):

```java
// Uses DefaultAzureCredential — tries multiple auth methods in order:
// 1. Environment variables
// 2. Managed Identity (in Azure)
// 3. Azure CLI credentials (local dev)
// 4. IntelliJ / VS Code credentials
builder.credential(new DefaultAzureCredentialBuilder().build());
```

To switch to Entra ID auth, update `application.properties`:

```properties
cosmos.auth.type=ENTRA_ID
```

> **Note:** For this workshop, we'll use key-based auth for simplicity.  
> In production, always prefer Entra ID with managed identity.

---

## Exercise: Run the Connectivity Demo

Open [src/main/java/com/cosmoslab/lab02/ConnectivityDemo.java](../src/main/java/com/cosmoslab/lab02/ConnectivityDemo.java)

This demo:
1. Creates a `CosmosClient` using the factory
2. Reads the database properties
3. Displays account and database information
4. Verifies the connection is working

### Run it:

```powershell
mvn compile exec:java -Dexec.mainClass="com.cosmoslab.lab02.ConnectivityDemo"
```

### Expected output:

```
[Auth] Using Key-based authentication
[Connection] Direct mode (production)
[CosmosClient] Initialized successfully -> https://...
=== Cosmos DB Connection Info ===
Database: workshop_jsmith
Database ID: workshop_jsmith
Connection verified successfully!
Client properties:
  - Connection Mode: DIRECT
  - Content Response on Write: true
  - Consistency Level: SESSION
```

---

## Best Practices Summary (for Relational Developers)

| What You Know (RDBMS) | Cosmos DB Equivalent |
|------------------------|---------------------|
| Connection string with pool size | CosmosClient with Direct/Gateway mode |
| Open/close connections per request | **Never** — reuse singleton client |
| SQL Login / Windows Auth | Key-based / Entra ID (AAD) |
| Connection pooling (HikariCP) | Built into CosmosClient (automatic) |
| Connection timeout settings | ThrottlingRetryOptions, timeouts |

---

## ✅ Checkpoint

- [ ] ConnectivityDemo runs and shows your database info
- [ ] You understand the difference between Direct and Gateway mode
- [ ] You know why CosmosClient must be a singleton
- [ ] You understand Key vs. Entra ID authentication

---

**Next:** [Lab 3 — Create Containers & Bulk Load Data](lab-03-containers-bulkload.md)
