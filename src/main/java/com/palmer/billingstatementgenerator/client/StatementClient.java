package com.palmer.billingstatementgenerator.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.palmer.billingstatementgenerator.models.lineitems.CashAdvanceLineItem;
import com.palmer.billingstatementgenerator.models.lineitems.MerchandiseLineItem;
import com.palmer.billingstatementgenerator.models.lineitems.ServiceLineItem;
import com.palmer.billingstatementgenerator.models.lineitems.SpecialChargeLineItem;
import com.palmer.billingstatementgenerator.models.statement.PdfResult;
import com.palmer.billingstatementgenerator.models.statement.SavedStatementSummary;
import com.palmer.billingstatementgenerator.models.statement.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StatementClient {
    private static final Logger log = LoggerFactory.getLogger(StatementClient.class);

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public int getNextControlNumber() {
        try {
            HttpResponse<String> response = getResponse("/next-control-number");

            String nextControlNumberString = response.body().trim();

            if (nextControlNumberString.isEmpty()) {
                return 1001;
            }

            return Integer.parseInt(nextControlNumberString);

        } catch (Exception e) {
            log.error("Failed to get next control number", e);
            throw new RuntimeException("Failed to get next control number", e);
        }
    }

    public List<SavedStatementSummary> getAllStatements() {
        log.debug("Getting all saved statements");
        try {
            HttpResponse<String> response = getResponse();

            List<SavedStatementSummary> statements = new ArrayList<>();

            mapper.readTree(response.body()).forEach(node -> {
                JsonNode serviceDate = node.get("serviceDate");
                JsonNode savedAt = node.get("savedAt");

                statements.add(new SavedStatementSummary(
                        node.get("id").asInt(),
                        node.get("controlNumber").asInt(),
                        node.get("servicesForName").asText(),
                        !serviceDate.isNull() ? LocalDate.parse(serviceDate.asText()) : null,
                        !savedAt.isNull() ? LocalDateTime.parse(savedAt.asText()) : null
                ));
            });

            log.debug("Returning all saved statements {}", statements);
            return statements;
        } catch (Exception e) {
            log.error("Failed to get saved statements", e);
            throw new RuntimeException("Failed to get statements", e);
        }
    }

    public int load(int id, Statement statement) {
        try {
            HttpResponse<String> response = getResponse("/" + id);

            JsonNode root = mapper.readTree(response.body());

            JsonNode serviceDate = root.get("serviceDate");
            JsonNode dateOfDeath = root.get("dateOfDeath");
            JsonNode packageId = root.get("packageId");

            statement.setControlNumber(root.get("controlNumber").asInt());
            statement.setServicesForName(root.get("servicesForName").asText());
            statement.setDateOfDeath(!dateOfDeath.isNull() ? LocalDate.parse(dateOfDeath.asText()) : null);
            statement.setPlaceOfDeath(root.get("placeOfDeath").asText());
            statement.setServiceDate(!serviceDate.isNull() ? LocalDate.parse(serviceDate.asText()) : null);
            statement.setReasonForEmbalming(root.get("reasonForEmbalming").asText());
            statement.setSalesTaxRate(root.get("salesTaxRate").decimalValue());
            statement.setPayment(root.get("payment").decimalValue());

            loadServices(statement, root);
            loadCashAdvances(statement, root);
            loadSpecialCharges(statement, root);
            loadMerchandise(statement, root);

            return !packageId.isNull() ? packageId.asInt() : 0;

        } catch (Exception e) {
            log.error("Failed to get saved statements id={}", id, e);
            throw new RuntimeException("Failed to get saved statements id=" + id, e);
        }
    }

    public int save(Statement statement) {
        ObjectNode body = buildStatementBody(statement);

        try {
            HttpRequest request = ApiConfig.authenticatedRequest(URI.create(ApiConfig.getStatementsUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = ApiConfig.getHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            return mapper.readTree(response.body()).get("id").asInt();
        } catch (Exception e) {
            log.error("Failed to send statements", e);
            throw new RuntimeException("Failed to send statements", e);
        }
    }

    public void update(int id, Statement statement) {
        ObjectNode body = buildStatementBody(statement);

        try {
            HttpRequest request = ApiConfig.authenticatedRequest(URI.create(ApiConfig.getStatementsUrl() + "/" + id))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

            ApiConfig.getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.error("Failed to update statement id={}", id, e);
            throw new RuntimeException("Failed to update statement id=" + id, e);
        }
    }

    public PdfResult getPdf(int id) {
        try {
            HttpRequest request = ApiConfig.authenticatedRequest(URI.create(ApiConfig.getStatementsUrl() + String.format("/%d/pdf", id)))
                    .GET()
                    .build();

            HttpResponse<byte[]> response = ApiConfig.getHttpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("PDF generation failed — status " + response.statusCode());
            }

            String disposition = response.headers().firstValue("Content-Disposition").orElse("");

            String filename = Pattern.compile("filename=\"?([^\"\\s;]+)\"?")
                    .matcher(disposition)
                    .results()
                    .findFirst()
                    .map(m -> m.group(1))
                    .orElse("statement.pdf");

            return new PdfResult(response.body(), filename);
        } catch (Exception e) {
            log.error("Failed to get PDF for statement id={}", id, e);
            throw new RuntimeException("Failed to get PDF for statement " + id, e);
        }
    }

    private static ObjectNode buildStatementBody(Statement statement) {
        ObjectNode body = mapper.createObjectNode();

        body.put("controlNumber", statement.getControlNumber());
        body.put("servicesForName", statement.getServicesForName());

        if (statement.getDateOfDeath() != null) {
            body.put("dateOfDeath", statement.getDateOfDeath().toString());
        } else {
            body.putNull("dateOfDeath");
        }

        body.put("placeOfDeath", statement.getPlaceOfDeath());

        if (statement.getServiceDate() != null) {
            body.put("serviceDate", statement.getServiceDate().toString());
        } else {
            body.putNull("serviceDate");
        }

        body.put("reasonForEmbalming", statement.getReasonForEmbalming());
        body.put("salesTaxRate",  statement.getSalesTaxRate());

        if (statement.getSelectedPackage() != null) {
            ObjectNode servicePackage = body.putObject("servicePackage");
            servicePackage.put("id", statement.getSelectedPackage().id());
            servicePackage.put("sortOrder", statement.getSelectedPackage().sortOrder());
            servicePackage.put("name", statement.getSelectedPackage().name());
            servicePackage.put("defaultCost",  statement.getSelectedPackage().defaultCost());
            servicePackage.put("legacyPackage", statement.getSelectedPackage().legacyPackage());
        } else {
            body.putNull("servicePackage");
        }

        body.put("payment", statement.getPayment());

        ArrayNode services = body.putArray("services");
        statement.getServices().stream()
                .filter(ServiceLineItem::isSelected)
                .forEach(item -> {
                    ObjectNode service = services.addObject();
                    service.put("serviceId", item.getCatalog().id());
                    service.put("inPackage", item.isInPackage());
                    service.put("name", item.getCatalog().name());
                    service.put("price",  item.getPrice());
                    service.put("description", item.getDescription());
                });

        ArrayNode merchandise = body.putArray("merchandise");
        statement.getMerchandise().stream()
                .filter(MerchandiseLineItem::isSelected)
                .forEach(item -> {
                    ObjectNode merchandiseItem = merchandise.addObject();
                    merchandiseItem.put("merchandiseId", item.getCatalog().id());
                    merchandiseItem.put("name", item.getCatalog().name());
                    merchandiseItem.put("price", item.getPrice());
                    merchandiseItem.put("description", item.getDescription());
                });

        ArrayNode specialCharges = body.putArray("specialCharges");
        statement.getSpecialCharges().stream()
                .filter(SpecialChargeLineItem::isSelected)
                .forEach(item -> {
                    ObjectNode specialChargeItem = specialCharges.addObject();
                    specialChargeItem.put("specialChargeId", item.getCatalog().id());
                    specialChargeItem.put("name", item.getCatalog().name());
                    specialChargeItem.put("price",  item.getPrice());
                    specialChargeItem.put("description", item.getDescription());
                });

        ArrayNode cashAdvances = body.putArray("cashAdvances");
        statement.getCashAdvances().stream()
                .filter(CashAdvanceLineItem::isSelected)
                .forEach(item -> {
                    ObjectNode cashAdvanceItem = cashAdvances.addObject();
                    cashAdvanceItem.put("cashAdvanceId", item.getCatalog().id());
                    cashAdvanceItem.put("name", item.getCatalog().name());
                    cashAdvanceItem.put("amount", item.getAmount());
                    cashAdvanceItem.put("provider", item.getProvider());
                });
        return body;
    }

    private static void loadServices(Statement statement, JsonNode root) {
        Map<Integer, ServiceLineItem> lineItems = statement.getServices().stream()
                .collect(Collectors.toMap(i -> i.getCatalog().id(), Function.identity()));
        statement.getServices().forEach(line -> {
            line.setSelected(false);
            line.setInPackage(false);
        });

        for (JsonNode node : root.get("services")) {
            ServiceLineItem item = lineItems.get(node.get("serviceId").asInt());
            if (item != null) {
                item.setInPackage(node.get("inPackage").asBoolean());
                item.setDescription(node.get("description").asText(""));
                item.setPrice(node.get("price").decimalValue());
                item.setSelected(true);
            }
        }
    }

    private static void loadMerchandise(Statement statement, JsonNode root) {
        Map<Integer, MerchandiseLineItem> lineItems = statement.getMerchandise().stream()
                .collect(Collectors.toMap(i -> i.getCatalog().id(), Function.identity()));
        statement.getMerchandise().forEach(line -> line.setSelected(false));
        for (JsonNode node : root.get("merchandise")) {
            MerchandiseLineItem item = lineItems.get(node.get("merchandiseId").asInt());
            if (item != null) {
                item.setPrice(node.get("price").decimalValue());
                item.setDescription(node.get("description").asText());
                item.setSelected(true);
            }
        }
    }

    private static void loadSpecialCharges(Statement statement, JsonNode root) {
        Map<Integer, SpecialChargeLineItem> lineItems = statement.getSpecialCharges().stream()
                .collect(Collectors.toMap(i -> i.getCatalog().id(), Function.identity()));
        statement.getSpecialCharges().forEach(line -> line.setSelected(false));
        for (JsonNode node : root.get("specialCharges")) {
            SpecialChargeLineItem item = lineItems.get(node.get("specialChargeId").asInt());
            if (item != null) {
                item.setPrice(node.get("price").decimalValue());
                item.setDescription(node.get("description").asText());
                item.setSelected(true);
            }
        }
    }

    private static void loadCashAdvances(Statement statement, JsonNode root) {
        Map<Integer, CashAdvanceLineItem> lineItems = statement.getCashAdvances().stream()
                .collect(Collectors.toMap(i -> i.getCatalog().id(), Function.identity()));
        statement.getCashAdvances().forEach(line -> line.setSelected(false));
        for (JsonNode node : root.get("cashAdvances")) {
            CashAdvanceLineItem item = lineItems.get(node.get("cashAdvanceId").asInt());
            if (item != null) {
                item.setAmount(node.get("amount").decimalValue());
                item.setProvider(node.get("provider").asText());
                item.setSelected(true);
            }
        }
    }

    private static HttpResponse<String> getResponse(String... endPoints) throws IOException, InterruptedException {
        String statementsUrl = ApiConfig.getStatementsUrl();

        HttpRequest request = ApiConfig.authenticatedRequest(URI.create(endPoints.length > 0 ? statementsUrl + endPoints[0] : statementsUrl))
                .GET()
                .build();

        return ApiConfig
                .getHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
    }
}
