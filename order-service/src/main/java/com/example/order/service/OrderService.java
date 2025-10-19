package com.example.order.service;

import com.example.order.dto.OrderResponse;
import com.example.order.entity.Order;
import com.example.order.entity.SagaState;
import com.example.order.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SagaOrchestrator sagaOrchestrator;

    @Autowired
    private OrderRepository orderRepository;

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

    @KafkaListener(topics = "payment-events", groupId = "order-service-group")
    public void handlePaymentEvent(String message) {
        try {
            logger.info("Received payment event: {}", message);
            
            JsonNode event = objectMapper.readTree(message);
            String eventType = event.get("eventType").asText();
            
            if ("PaymentProcessed".equals(eventType)) {
                processPaymentSuccess(event);
            } else if ("PaymentFailed".equals(eventType)) {
                processPaymentFailure(event);
            } else if ("PaymentCancelled".equals(eventType)) {
                processPaymentCancelled(event);
            }
            
        } catch (Exception e) {
            logger.error("Error processing payment event: {}", message, e);
            sendToDeadLetterQueue(message, "PaymentEventException", e.getMessage());
        }
    }

    private void processPaymentSuccess(JsonNode event) {
        Long orderId = event.get("orderId").asLong();
        Long paymentId = event.get("paymentId").asLong();
        logger.info("Payment successful for order: {}, payment: {}", orderId, paymentId);
        
        sagaOrchestrator.processPayment(orderId, paymentId);
        sagaOrchestrator.completeSaga(orderId);
        
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            order.setStatus("COMPLETED");
            orderRepository.save(order);
            logger.info("Order {} status updated to COMPLETED", orderId);
        }
    }

    private void processPaymentFailure(JsonNode event) {
        Long orderId = event.get("orderId").asLong();
        logger.error("Payment failed for order: {}", orderId);
        
        sagaOrchestrator.failSaga(orderId);
        
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            order.setStatus("FAILED");
            orderRepository.save(order);
            logger.info("Order {} status updated to FAILED", orderId);
        }
    }

    private void processPaymentCancelled(JsonNode event) {
        Long orderId = event.get("orderId").asLong();
        logger.info("Payment cancelled for order: {}", orderId);
        
        sagaOrchestrator.refundPayment(orderId);
        
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            order.setStatus("REFUNDED");
            orderRepository.save(order);
            logger.info("Order {} status updated to REFUNDED", orderId);
        }
    }

    private void processOrderCreated(JsonNode event) {
        String customerId = event.get("customerId").asText();
        String productId = event.get("productId").asText();
        Integer quantity = event.get("quantity").asInt();
        BigDecimal amount = new BigDecimal(event.get("amount").asText());
        String correlationId = event.has("correlationId") ? event.get("correlationId").asText() : null;

        Order order = new Order(customerId, productId, quantity, amount, "WAITING");
        order = orderRepository.save(order);
        Long orderId = order.getId();

        logger.info("Order created with ID: {} with status WAITING", orderId);

        if (correlationId != null) {
            publishOrderCreatedResponse(orderId, correlationId);
        }

        sagaOrchestrator.startSaga(orderId, customerId, productId, quantity, amount);
    }

    private void publishOrderCreatedResponse(Long orderId, String correlationId) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("orderId", orderId);
            response.put("correlationId", correlationId);
            response.put("status", "PENDING");

            String responseJson = objectMapper.writeValueAsString(response);
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
            kafkaTemplate.send("order-response", correlationId, responseJson);
            logger.info("Published order response for orderId: {}", orderId);
        } catch (Exception e) {
            logger.error("Failed to publish order response", e);
        }
    }

    public OrderResponse getOrder(Long orderId) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            Optional<SagaState> sagaState = sagaOrchestrator.getSagaState(order.getId());
            if (sagaState.isPresent()) {
                String sagaStatus = sagaState.get().getStatus();
                if ("NO_PAYMENT".equals(sagaStatus)) {
                    order.setStatus("FAILED");
                    orderRepository.save(order);
                }
            }
            return new OrderResponse(order.getId(), order.getCustomerId(), order.getProductId(), 
                order.getQuantity(), order.getAmount(), order.getStatus());
        }
        return null;
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
