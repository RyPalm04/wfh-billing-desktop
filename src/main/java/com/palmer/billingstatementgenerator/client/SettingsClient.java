package com.palmer.billingstatementgenerator.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.palmer.billingstatementgenerator.models.TenantSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class SettingsClient {
    private static final Logger log = LoggerFactory.getLogger(SettingsClient.class);

    private static final ObjectMapper mapper = new ObjectMapper();
    private final HttpResponse.BodyHandler<String> responseBodyHandler = HttpResponse.BodyHandlers.ofString();
    ;

    public TenantSettings getSettings() {
        try {
            HttpRequest request = ApiConfig.authenticatedRequest(URI.create(ApiConfig.getBaseUrl() + "/settings"))
                                           .GET()
                                           .build();

            HttpResponse<String> response = ApiConfig.getHttpClient().send(request, responseBodyHandler);

            JsonNode node = mapper.readTree(response.body());

            return new TenantSettings()
                    .setSalesTaxRate(node.get("salesTaxRate").decimalValue());
        } catch (Exception e) {
            log.error("Failed to load settings", e);
            throw new RuntimeException("Failed to load settings", e);
        }
    }

    public void updateSettings(TenantSettings settings) {
        try {
            ObjectNode node = mapper.createObjectNode();

            if (settings.getSalesTaxRate() != null) {
                node.put("salesTaxRate", settings.getSalesTaxRate());
            }

            HttpRequest request = ApiConfig.authenticatedRequest(URI.create(ApiConfig.getBaseUrl() + "/settings"))
                                           .header("Content-Type", "application/json")
                                           .PUT(HttpRequest.BodyPublishers.ofString(node.toString()))
                                           .build();

            ApiConfig.getHttpClient().send(request, responseBodyHandler);
        } catch (Exception e) {
            log.error("Failed to update settings", e);
            throw new RuntimeException("Failed to update settings", e);
        }
    }
}
