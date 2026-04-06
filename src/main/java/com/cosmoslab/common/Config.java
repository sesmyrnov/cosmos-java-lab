package com.cosmoslab.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration loader for Cosmos DB workshop labs.
 */
public class Config {

    private static final Properties props = new Properties();

    static {
        try (InputStream is = Config.class.getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (is == null) {
                throw new RuntimeException("application.properties not found on classpath");
            }
            props.load(is);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load application.properties", e);
        }
    }

    public static String getEndpoint() {
        return getRequired("cosmos.endpoint");
    }

    public static String getKey() {
        return getRequired("cosmos.key");
    }

    public static String getAuthType() {
        return props.getProperty("cosmos.auth.type", "KEY");
    }

    public static String getConnectionMode() {
        return props.getProperty("cosmos.connection.mode", "DIRECT");
    }

    public static String getDatabaseName() {
        return getRequired("cosmos.database");
    }

    public static String getStocksContainer() {
        return props.getProperty("cosmos.container.stocks", "stocks");
    }

    public static String getAccountsContainer() {
        return props.getProperty("cosmos.container.accounts", "accounts");
    }

    public static String getTimeSeriesContainer() {
        return props.getProperty("cosmos.container.timeseries", "stock_time_series");
    }

    public static String getLeasesContainer() {
        return props.getProperty("cosmos.container.leases", "leases");
    }

    private static String getRequired(String key) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank() || value.contains("<")) {
            throw new RuntimeException(
                    "Configuration '" + key + "' is missing or not set. " +
                    "Please update src/main/resources/application.properties");
        }
        return value;
    }
}
