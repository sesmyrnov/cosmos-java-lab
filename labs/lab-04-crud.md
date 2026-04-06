# Lab 4: CRUD Operations

| Duration | ~20 minutes |
|----------|------------|
| **Goal** | Master Create, Read, Update (Replace & Patch), and Delete operations on account/portfolio documents |

---

## Key Concepts

### CRUD in Cosmos DB vs. Relational

| Operation | SQL | Cosmos DB SDK | RU Cost |
|-----------|-----|---------------|---------|
| **Create** | `INSERT INTO` | `container.createItem()` | ~6-15 RU (by size) |
| **Read** | `SELECT ... WHERE id = ?` | `container.readItem()` (**point read**) | ~1 RU per KB |
| **Replace** | `UPDATE ... SET ... WHERE id = ?` | `container.replaceItem()` | ~10-15 RU |
| **Patch** | `UPDATE col1 = ? WHERE id = ?` | `container.patchItem()` | ~6-10 RU |
| **Delete** | `DELETE FROM ... WHERE id = ?` | `container.deleteItem()` | ~6-12 RU |

### Replace vs. Patch — When to Use Which

| Replace | Patch |
|---------|-------|
| Replaces the **entire document** | Updates **specific fields** only |
| Must read first, modify, then replace | No read needed — server-side operation |
| Higher RU (sends full document) | Lower RU (sends only changes) |
| Use for major document changes | Use for field updates, array operations, increments |

### Optimistic Concurrency with ETags

Every Cosmos DB document has an `_etag` property that changes on each update:

```java
// Read the document — get its current ETag
CosmosItemResponse<Account> response = container.readItem(id, partitionKey, Account.class);
String etag = response.getETag();

// Replace with ETag check — fails with 412 if someone else modified it
CosmosItemRequestOptions options = new CosmosItemRequestOptions();
options.setIfMatchETag(etag);    // Optimistic concurrency
container.replaceItem(account, id, partitionKey, options);
```

> **Relational parallel:** This is like `UPDATE ... WHERE version = @expectedVersion`  
> or using a `ROWVERSION`/`TIMESTAMP` column for optimistic locking.

---

## Code Walkthrough

Open [src/main/java/com/cosmoslab/lab04/CrudOperationsApp.java](../src/main/java/com/cosmoslab/lab04/CrudOperationsApp.java)

### Create — New Account

```java
Account account = new Account();
account.setId("ACC-NEW-001");
account.setAccountId("ACC-NEW-001");     // Partition key value
account.setOwnerName("Workshop User");
// ...
CosmosItemResponse<Account> response = container.createItem(
    account, new PartitionKey(account.getAccountId()), new CosmosItemRequestOptions());

System.out.println("Created! RU: " + response.getRequestCharge());
Account created = response.getItem();   // Works because contentResponseOnWriteEnabled=true
```

### Read — Point Read (Fastest Operation)

```java
// BEST PRACTICE: Use readItem when you know both id AND partition key
// This is a point read — costs exactly 1 RU per KB, bypasses query engine
CosmosItemResponse<Account> response = container.readItem(
    "ACC-001",                          // Document id
    new PartitionKey("ACC-001"),        // Partition key value
    Account.class);

Account account = response.getItem();
System.out.println("Read: " + account.getOwnerName());
System.out.println("RU cost: " + response.getRequestCharge());
```

### Update — Full Replace

```java
// Step 1: Read the current document
CosmosItemResponse<Account> readResponse = container.readItem(
    "ACC-001", new PartitionKey("ACC-001"), Account.class);
Account account = readResponse.getItem();

// Step 2: Modify the document
account.setCashBalance(account.getCashBalance() + 10000.00);
account.setUpdTimestamp(Instant.now().toString());

// Step 3: Replace with ETag for optimistic concurrency
CosmosItemRequestOptions options = new CosmosItemRequestOptions();
options.setIfMatchETag(readResponse.getETag());

CosmosItemResponse<Account> replaceResponse = container.replaceItem(
    account, account.getId(), new PartitionKey(account.getAccountId()), options);

System.out.println("Replaced! RU: " + replaceResponse.getRequestCharge());
```

### Update — Patch (Partial Update)

```java
// BEST PRACTICE: Use Patch for atomic field updates without reading first
CosmosPatchOperations patchOps = CosmosPatchOperations.create()
    .set("/cashBalance", 55000.00)                    // Set specific field
    .set("/updTimestamp", Instant.now().toString())    // Update timestamp
    .set("/riskProfile", "Moderate");                  // Change risk profile

CosmosItemResponse<Account> patchResponse = container.patchItem(
    "ACC-001",
    new PartitionKey("ACC-001"),
    patchOps,
    new CosmosPatchItemRequestOptions(),
    Account.class);

System.out.println("Patched! RU: " + patchResponse.getRequestCharge());
```

Patch supports these operations:
- `set(path, value)` — Set or overwrite a field
- `add(path, value)` — Add to an array or set new field
- `remove(path)` — Remove a field
- `replace(path, value)` — Replace existing field (fails if missing)
- `increment(path, value)` — Atomic counter increment (no read needed)

### Delete

```java
CosmosItemResponse<Object> deleteResponse = container.deleteItem(
    "ACC-NEW-001",
    new PartitionKey("ACC-NEW-001"),
    new CosmosItemRequestOptions());

System.out.println("Deleted! RU: " + deleteResponse.getRequestCharge());
```

---

## Exercise: Run the CRUD Demo

The demo app provides an interactive menu to practice each operation:

```powershell
mvn compile exec:java -Dexec.mainClass="com.cosmoslab.lab04.CrudOperationsApp"
```

### Menu:

```
=== Cosmos DB CRUD Operations ===
1. Create a new account
2. Read an account (point read)
3. Update account - Full Replace
4. Update account - Patch (partial)
5. Add holding to account portfolio (Patch)
6. Delete an account
7. Exit
Choose operation (1-7):
```

Try each operation and observe:
- The **RU cost** for each operation type
- How **Replace** requires a read first, but **Patch** doesn't
- How **ETag** prevents conflicting updates

---

## ✅ Checkpoint

- [ ] You can create, read, replace, patch, and delete account documents
- [ ] You understand Replace (full document) vs. Patch (partial update)
- [ ] You see how RU costs differ between operations
- [ ] You understand ETag-based optimistic concurrency

---

**Next:** [Lab 5 — Point Reads & Query Options](lab-05-queries.md)
