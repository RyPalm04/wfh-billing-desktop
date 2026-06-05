package com.palmer.billingstatementgenerator.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.util.Properties;

public class ApiConfig {
    private static final Logger log = LoggerFactory.getLogger(ApiConfig.class);

    private static final String CONFIG_DIR = System.getProperty("user.home") + "/.statement-generator";
    private static final String CONFIG_PATH = CONFIG_DIR + "/api.properties";
    private static String baseUrl;
    private static HttpClient httpClient;
    private static String apiKey = "";
    private static String tenantId = "";

    private ApiConfig() {
    }

    public static synchronized void init() {
        if (baseUrl != null) {
            log.debug("Base URL is already initialized");
            return;
        }

        if (!new File(CONFIG_DIR).mkdirs()) {
            log.error("Unable to create configuration directory");
        }

        Properties config = loadConfig();

        baseUrl = config.getProperty("api.base-url", "").trim();
        if (baseUrl.isEmpty()) {
            log.error("api.base-url not set in {}", CONFIG_PATH);
            throw new IllegalStateException("api.base-url not set in " + CONFIG_PATH);
        }

        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        String apiKey = config.getProperty("api.key", "").trim();
        if (!apiKey.isEmpty()) {
            ApiConfig.apiKey = apiKey;
        }

        String tenantId = config.getProperty("api.tenant-id", "").trim();
        if (!tenantId.isEmpty()) {
            ApiConfig.tenantId = tenantId;
        }

        httpClient = HttpClient.newHttpClient();

        log.info("API configured - base URL: {}", baseUrl);
    }

    public static String getBaseUrl() {
        return baseUrl;
    }

    public static String getCatalogUrl() {
        return getBaseUrl() + "/catalog";
    }

    public static String getStatementsUrl() {
        return getBaseUrl() + "/statements";
    }

    public static HttpClient getHttpClient() {
        if (httpClient == null) {
            log.error("API http client not initialized");
            throw new IllegalStateException("ApiConfig.init() not called");
        }

        return httpClient;
    }

    public static HttpRequest.Builder authenticatedRequest(URI uri) {
        if (apiKey.isEmpty()) {
            return HttpRequest.newBuilder(uri);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                                                .header("X-Api-Key", apiKey);

        if (!tenantId.isEmpty()) {
            builder.header("X-Tenant-Id", tenantId);
        }

        return builder;
    }

    private static Properties loadConfig() {
        File configFile = new File(CONFIG_PATH);
        if (configFile.exists()) {
            Properties props = new Properties();
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
            } catch (IOException e) {
                log.error("Failed to read API configuration from {}", CONFIG_PATH, e);
                throw new RuntimeException("Failed to read API configuration from " + CONFIG_PATH, e);
            }
            return props;
        }

        try (InputStream is = ApiConfig.class.getResourceAsStream("/api.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                log.info("Loaded API config from bundled defaults");
                return props;
            }
        } catch (IOException e) {
            log.error("Failed to read bundled API configuration", e);
            throw new RuntimeException("Failed to read bundled API configuration", e);
        }

        log.error("No API configuration found — expected user config at {} or bundled api.properties", CONFIG_PATH);
        throw new IllegalStateException("API configuration not found. Please create " + CONFIG_PATH + " with api.base-url set.");
    }
}
