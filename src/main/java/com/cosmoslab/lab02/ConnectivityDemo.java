package com.cosmoslab.lab02;

import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosDatabaseProperties;
import com.cosmoslab.common.Config;
import com.cosmoslab.common.CosmosClientFactory;

/**
 * Lab 2: Connectivity Demo
 * Demonstrates creating a CosmosClient and verifying the connection.
 */
public class ConnectivityDemo {

    public static void main(String[] args) {
        CosmosClient client = null;
        try {
            // Step 1: Get the singleton CosmosClient
            client = CosmosClientFactory.getClient();

            // Step 2: Get a reference to the database
            String databaseName = Config.getDatabaseName();
            CosmosDatabase database = client.getDatabase(databaseName);

            // Step 3: Read database properties to verify connection
            CosmosDatabaseProperties dbProps = database.read().getProperties();

            System.out.println("\n=== Cosmos DB Connection Info ===");
            System.out.println("Database: " + databaseName);
            System.out.println("Database ID: " + dbProps.getId());
            System.out.println("Connection verified successfully!");
            System.out.println("\nClient properties:");
            System.out.println("  - Connection Mode: " + Config.getConnectionMode());
            System.out.println("  - Auth Type: " + Config.getAuthType());
            System.out.println("  - Content Response on Write: true");
            System.out.println("  - Consistency Level: SESSION");

        } catch (Exception e) {
            System.err.println("Connection FAILED: " + e.getMessage());
            System.err.println("\nTroubleshooting:");
            System.err.println("  1. Check application.properties has correct endpoint and key");
            System.err.println("  2. If using emulator, ensure it's running and connection mode is GATEWAY");
            System.err.println("  3. If using cloud account, verify the key hasn't been rotated");
            e.printStackTrace();
        } finally {
            CosmosClientFactory.closeClient();
        }
    }
}
