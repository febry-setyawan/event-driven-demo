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

    private void processOrderCreated(JsonNode event) {
        Long orderId = event.get("orderId").asLong();
        String customerId = event.get("customerId").asText();
        String productId = event.get("productId").asText();
        Integer quantity = event.get("quantity").asInt();
        BigDecimal amount = new BigDecimal(event.get("amount").asText());

        Order order = new Order(customerId, productId, quantity, amount, "PROCESSING");
        order = orderRepository.save(order);
        Long dbOrderId = order.getId();

        logger.info("Processing order: {} (API order_id: {})", dbOrderId, orderId);

        sagaOrchestrator.startSaga(dbOrderId, customerId, productId, quantity, amount);

        Optional<SagaState> sagaState = sagaOrchestrator.getSagaState(dbOrderId);
        if (sagaState.isPresent()) {
            order.setStatus(sagaState.get().getStatus());
            orderRepository.save(order);
        }
    }

    public OrderResponse getOrder(Long orderId) {
        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            Optional<SagaState> sagaState = sagaOrchestrator.getSagaState(orderId);
            if (sagaState.isPresent()) {
                order.setStatus(sagaState.get().getStatus());
                orderRepository.save(order);
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
