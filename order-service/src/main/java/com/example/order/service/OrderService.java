package com.example.order.service;

import com.example.order.dto.OrderResponse;
import com.example.order.dto.PaymentRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    
    @Autowired
    private ObjectMapper objectMapper;

    @Value("${payment.service.url:http://payment-service:8082}")
    private String paymentServiceUrl;

    private final WebClient webClient = WebClient.builder().build();
    private final ConcurrentHashMap<Long, OrderResponse> orders = new ConcurrentHashMap<>();

    @KafkaListener(topics = "order-events", groupId = "order-service-group")
    public void handleOrderEvent(String message) {
        try {
            logger.info("Received order event: {}", message);
            
            JsonNode event = objectMapper.readTree(message);
            String eventType = event.get("eventType").asText();
            
            if ("OrderCreated".equals(eventType)) {
                processOrderCreated(event);
            }
            
        } catch (JsonProcessingException e) {
            logger.error("Error processing order event: {}", message, e);
            sendToDeadLetterQueue(message, "JsonProcessingException", e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error processing order event: {}", message, e);
            sendToDeadLetterQueue(message, "UnexpectedException", e.getMessage());
        }
    }

    private void processOrderCreated(JsonNode event) {
        Long orderId = event.get("orderId").asLong();
        String customerId = event.get("customerId").asText();
        String productId = event.get("productId").asText();
        Integer quantity = event.get("quantity").asInt();
        BigDecimal amount = new BigDecimal(event.get("amount").asText());

        // Store order in memory
        OrderResponse order = new OrderResponse(orderId, customerId, productId, quantity, amount, "PROCESSING");
        orders.put(orderId, order);

        logger.info("Processing order: {}", orderId);

        // Call Payment Service with retry
        PaymentRequest paymentRequest = new PaymentRequest(orderId, amount);
        
        webClient.post()
                .uri(paymentServiceUrl + "/payments")
                .bodyValue(paymentRequest)
                .retrieve()
                .bodyToMono(String.class)
                .retry(3) // Retry up to 3 times
                .subscribe(
                    response -> {
                        logger.info("Payment processed for order: {}", orderId);
                        order.setStatus("COMPLETED");
                    },
                    error -> {
                        logger.error("Payment failed for order: {} after retries", orderId, error);
                        order.setStatus("FAILED");
                        // In production: trigger compensation/rollback logic
                    }
                );
    }

    public OrderResponse getOrder(Long orderId) {
        return orders.get(orderId);
    }

    private void sendToDeadLetterQueue(String originalMessage, String errorType, String errorMessage) {
        try {
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("originalMessage", originalMessage);
            dlqMessage.put("errorType", errorType);
            dlqMessage.put("errorMessage", errorMessage);
            dlqMessage.put("service", "order-service");
            dlqMessage.put("timestamp", java.time.Instant.now().toString());
            
            String dlqJson = objectMapper.writeValueAsString(dlqMessage);
            
            org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate = 
                new org.springframework.kafka.core.KafkaTemplate<>(
                    new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(
                        Map.of(
                            "bootstrap.servers", "kafka:29092",
                            "key.serializer", "org.apache.kafka.common.serialization.StringSerializer",
                            "value.serializer", "org.apache.kafka.common.serialization.StringSerializer"
                        )
                    )
                );
            
            kafkaTemplate.send("dead-letter-queue", dlqJson);
            logger.info("Sent failed message to dead-letter-queue");
            
        } catch (Exception e) {
            logger.error("Failed to send message to dead-letter-queue", e);
        }
    }
}
