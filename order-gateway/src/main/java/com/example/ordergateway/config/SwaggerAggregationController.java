package com.example.ordergateway.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Iterator;
import java.util.Map;

@RestController
public class SwaggerAggregationController {

    @Value("${ORDER_SERVICE_URL:http://order-service:8081}")
    private String orderServiceUrl;

    @Value("${PAYMENT_SERVICE_URL:http://payment-service:8082}")
    private String paymentServiceUrl;

    private final WebClient webClient = WebClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/v3/api-docs/order-service")
    public Mono<ResponseEntity<String>> orderServiceDocs() {
        return webClient.get()
            .uri(orderServiceUrl + "/v3/api-docs")
            .retrieve()
            .bodyToMono(String.class)
            .map(ResponseEntity::ok)
            .onErrorReturn(ResponseEntity.notFound().build());
    }

    @GetMapping("/v3/api-docs/payment-service")
    public Mono<ResponseEntity<String>> paymentServiceDocs() {
        return webClient.get()
            .uri(paymentServiceUrl + "/v3/api-docs")
            .retrieve()
            .bodyToMono(String.class)
            .map(ResponseEntity::ok)
            .onErrorReturn(ResponseEntity.notFound().build());
    }

    @GetMapping("/v3/api-docs/gateway")
    public Mono<ResponseEntity<String>> gatewayDocs() {
        return webClient.get()
            .uri("http://localhost:8080/v3/api-docs")
            .retrieve()
            .bodyToMono(String.class)
            .map(ResponseEntity::ok)
            .onErrorReturn(ResponseEntity.notFound().build());
    }

    @GetMapping("/v3/api-docs/aggregated")
    public Mono<ResponseEntity<String>> aggregatedDocs() {
        Mono<String> gatewayMono = webClient.get()
            .uri("http://localhost:8080/v3/api-docs")
            .retrieve()
            .bodyToMono(String.class)
            .onErrorReturn("{}");

        Mono<String> orderMono = webClient.get()
            .uri(orderServiceUrl + "/v3/api-docs")
            .retrieve()
            .bodyToMono(String.class)
            .onErrorReturn("{}");

        Mono<String> paymentMono = webClient.get()
            .uri(paymentServiceUrl + "/v3/api-docs")
            .retrieve()
            .bodyToMono(String.class)
            .onErrorReturn("{}");

        return Mono.zip(gatewayMono, orderMono, paymentMono)
            .map(tuple -> {
                try {
                    JsonNode gateway = objectMapper.readTree(tuple.getT1());
                    JsonNode order = objectMapper.readTree(tuple.getT2());
                    JsonNode payment = objectMapper.readTree(tuple.getT3());

                    ObjectNode merged = objectMapper.createObjectNode();
                    merged.put("openapi", "3.0.1");
                    
                    ObjectNode info = merged.putObject("info");
                    info.put("title", "Event-Driven Architecture API");
                    info.put("version", "1.0");
                    info.put("description", "Aggregated API documentation for all services");

                    merged.set("servers", gateway.get("servers"));
                    merged.set("security", gateway.get("security"));

                    ObjectNode paths = merged.putObject("paths");
                    mergePaths(paths, gateway.get("paths"), "");
                    mergePaths(paths, order.get("paths"), "");
                    mergePaths(paths, payment.get("paths"), "");

                    ObjectNode components = merged.putObject("components");
                    components.set("securitySchemes", gateway.get("components").get("securitySchemes"));
                    
                    ObjectNode schemas = components.putObject("schemas");
                    mergeSchemas(schemas, gateway.get("components").get("schemas"));
                    mergeSchemas(schemas, order.get("components").get("schemas"));
                    mergeSchemas(schemas, payment.get("components").get("schemas"));

                    return ResponseEntity.ok(objectMapper.writeValueAsString(merged));
                } catch (Exception e) {
                    return ResponseEntity.status(500).body("{\"error\": \"Failed to aggregate docs\"}");
                }
            });
    }

    private void mergePaths(ObjectNode target, JsonNode source, String prefix) {
        if (source != null && source.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = source.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String path = prefix.isEmpty() ? entry.getKey() : prefix + entry.getKey();
                target.set(path, entry.getValue());
            }
        }
    }

    private void mergePathsWithPrefix(ObjectNode target, JsonNode source, String prefix) {
        mergePaths(target, source, prefix);
    }

    private void mergeSchemas(ObjectNode target, JsonNode source) {
        if (source != null && source.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = source.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                target.set(entry.getKey(), entry.getValue());
            }
        }
    }
}
