package com.palmer.billingstatementgenerator.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.palmer.billingstatementgenerator.AppInfo;
import javafx.stage.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class FeedbackClient {
    private static final Logger log = LoggerFactory.getLogger(FeedbackClient.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    public void submitFeedback(String type, String description, String currentTab) {
        try {
            ObjectNode metadata = mapper.createObjectNode()
                                        .put("page", currentTab)
                                        .put("userAgent", "Desktop / " + System.getProperty("os.name") + " " + System.getProperty("os.version"))
                                        .put("screenSize", (int) Screen.getPrimary().getBounds().getWidth() + "x" + (int) Screen.getPrimary().getBounds().getHeight())
                                        .put("platform", "desktop")
                                        .put("appVersion", AppInfo.VERSION);

            ObjectNode body = mapper.createObjectNode()
                                    .put("type", type)
                                    .put("description", description);
            body.set("metadata", metadata);

            String json = mapper.writeValueAsString(body);

            HttpRequest request = ApiConfig.authenticatedRequest(URI.create(ApiConfig.getBaseUrl() + "/feedback"))
                                           .header("Content-Type", "application/json")
                                           .POST(HttpRequest.BodyPublishers.ofString(json))
                                           .build();

            HttpResponse<String> response = ApiConfig.getHttpClient()
                                                     .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Feedback submission failed with status {}", response.statusCode());
                throw new RuntimeException("Feedback submission failed");
            }

            log.debug("Feedback submitted successfully");
        } catch (Exception e) {
            log.error("Failed to submit feedback", e);
            throw new RuntimeException("Failed to submit feedback", e);
        }
    }
}