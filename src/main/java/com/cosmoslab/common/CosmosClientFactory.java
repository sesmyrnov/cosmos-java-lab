package com.cosmoslab.common;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.ThrottlingRetryOptions;
import com.azure.identity.DefaultAzureCredentialBuilder;

import java.time.Duration;

/**
 * Factory for creating CosmosClient instances following best practices:
 * - Singleton pattern (reuse client)
 * - Direct mode for production, Gateway for emulator
 * - Entra ID or Key-based authentication
 * - Content response enabled on writes
 * - Retry configuration for 429 throttling
 */
public class CosmosClientFactory {

    private static CosmosClient instance;

    /**
     * Returns a singleton CosmosClient configured from application.properties.
     */
    public static synchronized CosmosClient getClient() {
        if (instance == null) {
            instance = createClient();
        }
        return instance;
    }

    private static CosmosClient createClient() {
        String endpoint = Config.getEndpoint();
        String connectionMode = Config.getConnectionMode();
        String authType = Config.getAuthType();

        // Configure retry options for 429 throttling
        ThrottlingRetryOptions retryOptions = new ThrottlingRetryOptions();
        retryOptions.setMaxRetryAttemptsOnThrottledRequests(9);
        retryOptions.setMaxRetryWaitTime(Duration.ofSeconds(30));

        CosmosClientBuilder builder = new CosmosClientBuilder()
                .endpoint(endpoint)
                .consistencyLevel(ConsistencyLevel.SESSION)
                .contentResponseOnWriteEnabled(true)   // Java SDK: returns item on writes
                .throttlingRetryOptions(retryOptions);

        // Authentication: Key-based or Entra ID
        if ("ENTRA_ID".equalsIgnoreCase(authType)) {
            System.out.println("[Auth] Using Entra ID (DefaultAzureCredential)");
            builder.credential(new DefaultAzureCredentialBuilder().build());
        } else {
            System.out.println("[Auth] Using Key-based authentication");
            builder.key(Config.getKey());
        }

        // Connection mode: Direct for production, Gateway for emulator
        if ("GATEWAY".equalsIgnoreCase(connectionMode)) {
            System.out.println("[Connection] Gateway mode (emulator or restricted network)");
            builder.gatewayMode();
        } else {
            System.out.println("[Connection] Direct mode (production)");
            builder.directMode();
        }

        CosmosClient client = builder.buildClient();
        System.out.println("[CosmosClient] Initialized successfully -> " + endpoint);
        return client;
    }

    /**
     * Closes the singleton client. Call during application shutdown.
     */
    public static synchronized void closeClient() {
        if (instance != null) {
            instance.close();
            instance = null;
            System.out.println("[CosmosClient] Closed.");
        }
    }
}
