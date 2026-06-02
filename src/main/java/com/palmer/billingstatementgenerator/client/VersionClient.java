package com.palmer.billingstatementgenerator.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class VersionClient {
    private static final Logger logger = LoggerFactory.getLogger(VersionClient.class);

    public String fetchApiVersion() {
        try {
            HttpRequest request = ApiConfig.authenticatedRequest(URI.create(ApiConfig.getBaseUrl() + "/version")).GET().build();

            HttpResponse<String> response = ApiConfig.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            }

            logger.warn("Failed to fetch API version, status{}", response.statusCode());
        } catch (Exception e) {
            logger.error("Failed to fetch API version", e);
        }

        return "unknown";
    }
}
