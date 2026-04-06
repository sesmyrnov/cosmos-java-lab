package com.cosmoslab.lab04;

import com.azure.cosmos.*;
import com.azure.cosmos.models.*;
import com.cosmoslab.common.Config;
import com.cosmoslab.common.CosmosClientFactory;
import com.cosmoslab.models.Account;
import com.cosmoslab.models.Holding;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Lab 4: CRUD Operations on accounts/portfolio documents.
 * Interactive console menu demonstrating Create, Read, Replace, Patch, and Delete.
 */
public class CrudOperationsApp {

    private static CosmosContainer accountsContainer;

    public static void main(String[] args) {
        CosmosClient client = null;
        try {
            client = CosmosClientFactory.getClient();
            CosmosDatabase database = client.getDatabase(Config.getDatabaseName());
            accountsContainer = database.getContainer(Config.getAccountsContainer());

            Scanner scanner = new Scanner(System.in);
            boolean running = true;

            while (running) {
                printMenu();
                String choice = scanner.nextLine().trim();

                switch (choice) {
                    case "1" -> createAccount(scanner);
                    case "2" -> readAccount(scanner);
                    case "3" -> replaceAccount(scanner);
                    case "4" -> patchAccount(scanner);
                    case "5" -> addHolding(scanner);
                    case "6" -> deleteAccount(scanner);
                    case "7" -> running = false;
                    default -> System.out.println("Invalid choice. Try again.");
                }
            }

            System.out.println("Goodbye!");

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            CosmosClientFactory.closeClient();
        }
    }

    private static void printMenu() {
        System.out.println("\n=== Cosmos DB CRUD Operations ===");
        System.out.println("1. Create a new account");
        System.out.println("2. Read an account (point read)");
        System.out.println("3. Update account - Full Replace");
        System.out.println("4. Update account - Patch (partial update)");
        System.out.println("5. Add holding to account portfolio (Patch)");
        System.out.println("6. Delete an account");
        System.out.println("7. Exit");
        System.out.print("Choose operation (1-7): ");
    }

    // ========================================================================
    // 1. CREATE — Insert a new account document
    // ========================================================================
    private static void createAccount(Scanner scanner) {
        System.out.print("Enter account ID (e.g., ACC-NEW-001): ");
        String accountId = scanner.nextLine().trim();

        System.out.print("Enter owner name: ");
        String ownerName = scanner.nextLine().trim();

        System.out.print("Enter account type (Individual/IRA/401k/Trust): ");
        String accountType = scanner.nextLine().trim();

        System.out.print("Enter initial cash balance: ");
        double cashBalance = Double.parseDouble(scanner.nextLine().trim());

        // Build the account document
        Account account = new Account();
        account.setId(accountId);
        account.setAccountId(accountId);              // Partition key
        account.setAccountName(ownerName + "'s Account");
        account.setAccountType(accountType);
        account.setOwnerName(ownerName);
        account.setEmail(ownerName.toLowerCase().replace(" ", ".") + "@example.com");
        account.setRiskProfile("Moderate");
        account.setTotalValue(cashBalance);
        account.setCashBalance(cashBalance);
        account.setCurrency("USD");
        account.setCreatedDate(Instant.now().toString().substring(0, 10));
        account.setUpdTimestamp(Instant.now().toString());
        account.setPortfolio(new ArrayList<>());

        try {
            // BEST PRACTICE: Each createItem call gets its own CosmosItemRequestOptions
            CosmosItemRequestOptions options = new CosmosItemRequestOptions();

            CosmosItemResponse<Account> response = accountsContainer.createItem(
                    account, new PartitionKey(account.getAccountId()), options);

            Account created = response.getItem();
            System.out.printf("%n[CREATE] Success!%n");
            System.out.printf("  Account ID: %s%n", created.getAccountId());
            System.out.printf("  Owner: %s%n", created.getOwnerName());
            System.out.printf("  RU cost: %.2f%n", response.getRequestCharge());

        } catch (CosmosException e) {
            if (e.getStatusCode() == 409) {
                System.out.println("[CREATE] Account already exists: " + accountId);
            } else {
                System.err.println("[CREATE] Failed: " + e.getMessage());
            }
        }
    }

    // ========================================================================
    // 2. READ — Point read (the most efficient operation in Cosmos DB)
    // ========================================================================
    private static void readAccount(Scanner scanner) {
        System.out.print("Enter account ID to read (e.g., ACC-001): ");
        String accountId = scanner.nextLine().trim();

        try {
            // BEST PRACTICE: Point read when you know both id AND partition key
            // This costs exactly 1 RU per KB and bypasses the query engine
            CosmosItemResponse<Account> response = accountsContainer.readItem(
                    accountId,
                    new PartitionKey(accountId),
                    Account.class);

            Account account = response.getItem();
            System.out.printf("%n[READ] Success!%n");
            System.out.printf("  Account ID: %s%n", account.getAccountId());
            System.out.printf("  Owner: %s%n", account.getOwnerName());
            System.out.printf("  Type: %s%n", account.getAccountType());
            System.out.printf("  Risk: %s%n", account.getRiskProfile());
            System.out.printf("  Total Value: $%,.2f%n", account.getTotalValue());
            System.out.printf("  Cash Balance: $%,.2f%n", account.getCashBalance());

            if (account.getPortfolio() != null && !account.getPortfolio().isEmpty()) {
                System.out.printf("  Portfolio (%d holdings):%n", account.getPortfolio().size());
                for (Holding h : account.getPortfolio()) {
                    System.out.printf("    %-6s | %4d shares | $%,10.2f | %+.2f%%%n",
                            h.getTicker(), h.getShares(), h.getMarketValue(), h.getGainLossPercent());
                }
            }

            System.out.printf("  RU cost: %.2f%n", response.getRequestCharge());
            System.out.printf("  ETag: %s%n", response.getETag());

        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                System.out.println("[READ] Account not found: " + accountId);
            } else {
                System.err.println("[READ] Failed: " + e.getMessage());
            }
        }
    }

    // ========================================================================
    // 3. REPLACE — Full document replacement with ETag concurrency check
    // ========================================================================
    private static void replaceAccount(Scanner scanner) {
        System.out.print("Enter account ID to update (e.g., ACC-001): ");
        String accountId = scanner.nextLine().trim();

        try {
            // Step 1: Read the current document (required for Replace)
            CosmosItemResponse<Account> readResponse = accountsContainer.readItem(
                    accountId, new PartitionKey(accountId), Account.class);

            Account account = readResponse.getItem();
            String etag = readResponse.getETag();

            System.out.println("Current values:");
            System.out.printf("  Owner: %s%n", account.getOwnerName());
            System.out.printf("  Cash Balance: $%,.2f%n", account.getCashBalance());
            System.out.printf("  Risk Profile: %s%n", account.getRiskProfile());

            // Step 2: Get the updates
            System.out.print("New cash balance (or press Enter to skip): ");
            String cashInput = scanner.nextLine().trim();
            if (!cashInput.isEmpty()) {
                account.setCashBalance(Double.parseDouble(cashInput));
            }

            System.out.print("New risk profile (Conservative/Moderate/Aggressive, or Enter to skip): ");
            String riskInput = scanner.nextLine().trim();
            if (!riskInput.isEmpty()) {
                account.setRiskProfile(riskInput);
            }

            account.setUpdTimestamp(Instant.now().toString());

            // Step 3: Replace with ETag check (optimistic concurrency)
            // If another process modified the document since we read it,
            // this will fail with HTTP 412 (Precondition Failed)
            CosmosItemRequestOptions options = new CosmosItemRequestOptions();
            options.setIfMatchETag(etag);

            CosmosItemResponse<Account> replaceResponse = accountsContainer.replaceItem(
                    account, account.getId(), new PartitionKey(account.getAccountId()), options);

            System.out.printf("%n[REPLACE] Success!%n");
            System.out.printf("  Cash Balance: $%,.2f%n", replaceResponse.getItem().getCashBalance());
            System.out.printf("  Risk Profile: %s%n", replaceResponse.getItem().getRiskProfile());
            System.out.printf("  RU cost (read + replace): %.2f + %.2f = %.2f%n",
                    readResponse.getRequestCharge(),
                    replaceResponse.getRequestCharge(),
                    readResponse.getRequestCharge() + replaceResponse.getRequestCharge());

        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                System.out.println("[REPLACE] Account not found: " + accountId);
            } else if (e.getStatusCode() == 412) {
                System.out.println("[REPLACE] Conflict! Document was modified by another process.");
                System.out.println("  (This is optimistic concurrency in action — read and try again)");
            } else {
                System.err.println("[REPLACE] Failed: " + e.getMessage());
            }
        }
    }

    // ========================================================================
    // 4. PATCH — Partial update without reading first
    // ========================================================================
    private static void patchAccount(Scanner scanner) {
        System.out.print("Enter account ID to patch (e.g., ACC-001): ");
        String accountId = scanner.nextLine().trim();

        System.out.print("New risk profile (Conservative/Moderate/Aggressive): ");
        String riskProfile = scanner.nextLine().trim();

        System.out.print("New cash balance: ");
        double cashBalance = Double.parseDouble(scanner.nextLine().trim());

        try {
            // BEST PRACTICE: Patch updates specific fields without reading first
            // This saves the cost of a read operation and avoids race conditions
            CosmosPatchOperations patchOps = CosmosPatchOperations.create()
                    .set("/riskProfile", riskProfile)
                    .set("/cashBalance", cashBalance)
                    .set("/updTimestamp", Instant.now().toString());

            CosmosItemResponse<Account> patchResponse = accountsContainer.patchItem(
                    accountId,
                    new PartitionKey(accountId),
                    patchOps,
                    new CosmosPatchItemRequestOptions(),
                    Account.class);

            Account patched = patchResponse.getItem();
            System.out.printf("%n[PATCH] Success!%n");
            System.out.printf("  Risk Profile: %s%n", patched.getRiskProfile());
            System.out.printf("  Cash Balance: $%,.2f%n", patched.getCashBalance());
            System.out.printf("  RU cost: %.2f (no prior read needed!)%n",
                    patchResponse.getRequestCharge());

        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                System.out.println("[PATCH] Account not found: " + accountId);
            } else {
                System.err.println("[PATCH] Failed: " + e.getMessage());
            }
        }
    }

    // ========================================================================
    // 5. PATCH — Add a holding to the portfolio array
    // ========================================================================
    private static void addHolding(Scanner scanner) {
        System.out.print("Enter account ID (e.g., ACC-001): ");
        String accountId = scanner.nextLine().trim();

        System.out.print("Stock ticker (e.g., MSFT): ");
        String ticker = scanner.nextLine().trim();

        System.out.print("Number of shares: ");
        int shares = Integer.parseInt(scanner.nextLine().trim());

        System.out.print("Cost basis per share: ");
        double costBasis = Double.parseDouble(scanner.nextLine().trim());

        System.out.print("Current price per share: ");
        double currentPrice = Double.parseDouble(scanner.nextLine().trim());

        Holding holding = new Holding(ticker, ticker + " Corp.", shares, costBasis, currentPrice);

        try {
            // Use Patch to append to the portfolio array
            CosmosPatchOperations patchOps = CosmosPatchOperations.create()
                    .add("/portfolio/-", holding)          // Append to end of array
                    .set("/updTimestamp", Instant.now().toString());

            CosmosItemResponse<Account> response = accountsContainer.patchItem(
                    accountId,
                    new PartitionKey(accountId),
                    patchOps,
                    new CosmosPatchItemRequestOptions(),
                    Account.class);

            Account updated = response.getItem();
            System.out.printf("%n[PATCH ADD] Success! Portfolio now has %d holdings%n",
                    updated.getPortfolio().size());
            System.out.printf("  RU cost: %.2f%n", response.getRequestCharge());

        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                System.out.println("[PATCH] Account not found: " + accountId);
            } else {
                System.err.println("[PATCH] Failed: " + e.getMessage());
            }
        }
    }

    // ========================================================================
    // 6. DELETE — Remove an account document
    // ========================================================================
    private static void deleteAccount(Scanner scanner) {
        System.out.print("Enter account ID to delete (e.g., ACC-NEW-001): ");
        String accountId = scanner.nextLine().trim();

        System.out.printf("Are you sure you want to delete %s? (yes/no): ", accountId);
        String confirm = scanner.nextLine().trim();

        if (!"yes".equalsIgnoreCase(confirm)) {
            System.out.println("Delete cancelled.");
            return;
        }

        try {
            CosmosItemResponse<Object> response = accountsContainer.deleteItem(
                    accountId,
                    new PartitionKey(accountId),
                    new CosmosItemRequestOptions());

            System.out.printf("%n[DELETE] Success! Account %s deleted.%n", accountId);
            System.out.printf("  RU cost: %.2f%n", response.getRequestCharge());

        } catch (CosmosException e) {
            if (e.getStatusCode() == 404) {
                System.out.println("[DELETE] Account not found: " + accountId);
            } else {
                System.err.println("[DELETE] Failed: " + e.getMessage());
            }
        }
    }
}
