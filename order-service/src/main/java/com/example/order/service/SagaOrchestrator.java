package com.example.order.service;

import com.example.order.dto.PaymentRequest;
import com.example.order.entity.SagaEvent;
import com.example.order.entity.SagaState;
import com.example.order.repository.SagaEventRepository;
import com.example.order.repository.SagaStateRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SagaOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(SagaOrchestrator.class);
    private static final String COMPENSATION_TOPIC = "compensation-events";

    @Autowired
    private SagaStateRepository sagaStateRepository;

    @Autowired
    private SagaEventRepository sagaEventRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${payment.service.url:http://payment-service:8082}")
    private String paymentServiceUrl;

    private final WebClient webClient = WebClient.builder().build();

    public String startSaga(Long orderId, String customerId, String productId, Integer quantity, BigDecimal amount) {
        String sagaId = UUID.randomUUID().toString();
        return startSagaWithId(sagaId, orderId, customerId, productId, quantity, amount);
    }

    public String startSagaWithId(String sagaId, Long orderId, String customerId, String productId, Integer quantity, BigDecimal amount) {
        logger.info("Starting saga {} for order: {}", sagaId, orderId);

        SagaState saga = new SagaState(sagaId, orderId, "WAITING", "ORDER_CREATED");
        saga = sagaStateRepository.save(saga);
        
        logSagaEvent(sagaId, "SAGA_STARTED", String.format("Order: %d, Customer: %s, Amount: %s", orderId, customerId, amount));
        
        logger.info("Saga {} state saved for order: {} with status WAITING", sagaId, orderId);
        return sagaId;
    }

    public void processPayment(Long orderId, Long paymentId) {
        SagaState saga = sagaStateRepository.findByOrderId(orderId).orElseThrow();
        saga.setStatus("PROCESSING");
        saga.setCurrentStep("PAYMENT_PROCESSING");
        saga.setPaymentId(paymentId);
        sagaStateRepository.save(saga);
        
        logSagaEvent(saga.getSagaId(), "PAYMENT_PROCESSING", String.format("Payment ID: %d", paymentId));
        
        logger.info("Payment processing started for order: {}, payment: {}", orderId, paymentId);
    }

    public void compensate(SagaState saga) {
        saga.setStatus("COMPENSATING");
        sagaStateRepository.save(saga);
        
        logSagaEvent(saga.getSagaId(), "COMPENSATION_STARTED", "Starting compensation for order: " + saga.getOrderId());

        logger.info("Starting compensation for order: {}", saga.getOrderId());

        if (saga.getPaymentId() != null) {
            cancelPayment(saga.getPaymentId());
            logSagaEvent(saga.getSagaId(), "PAYMENT_CANCELLED", "Payment ID: " + saga.getPaymentId());
        }

        cancelOrder(saga.getOrderId());
        logSagaEvent(saga.getSagaId(), "ORDER_CANCELLED", "Order ID: " + saga.getOrderId());

        saga.setStatus("FAILED");
        saga.setCurrentStep("COMPENSATED");
        sagaStateRepository.save(saga);
        
        logSagaEvent(saga.getSagaId(), "COMPENSATION_COMPLETED", "All compensations executed");

        logger.info("Compensation completed for order: {}", saga.getOrderId());
    }

    public void completeSaga(Long orderId) {
        SagaState saga = sagaStateRepository.findByOrderId(orderId).orElseThrow();
        saga.setStatus("COMPLETED");
        saga.setCurrentStep("PAYMENT_COMPLETED");
        sagaStateRepository.save(saga);
        
        logSagaEvent(saga.getSagaId(), "SAGA_COMPLETED", "Payment completed successfully");
        
        logger.info("Saga completed successfully for order: {}", orderId);
    }

    private void cancelPayment(Long paymentId) {
        try {
            webClient.post()
                    .uri(paymentServiceUrl + "/api/payments/" + paymentId + "/cancel")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            publishCompensationEvent("PaymentCancelled", paymentId);
            logger.info("Payment cancelled: {}", paymentId);

        } catch (Exception e) {
            logger.error("Failed to cancel payment: {}", paymentId, e);
        }
    }

    private void cancelOrder(Long orderId) {
        try {
            publishCompensationEvent("OrderCancelled", orderId);
            logger.info("Order cancelled: {}", orderId);
        } catch (Exception e) {
            logger.error("Failed to cancel order: {}", orderId, e);
        }
    }

    private void logSagaEvent(String sagaId, String eventType, String eventData) {
        try {
            SagaEvent event = new SagaEvent(sagaId, eventType, eventData);
            sagaEventRepository.save(event);
            logger.debug("Logged saga event: {} for saga: {}", eventType, sagaId);
        } catch (Exception e) {
            logger.error("Failed to log saga event: {} for saga: {}", eventType, sagaId, e);
        }
    }

    private void publishCompensationEvent(String eventType, Long entityId) {
        try {
            String idempotencyKey = UUID.randomUUID().toString();
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", eventType);
            event.put("entityId", entityId);
            event.put("idempotencyKey", idempotencyKey);
            event.put("timestamp", Instant.now().toString());

            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(COMPENSATION_TOPIC, entityId.toString(), eventJson);

            logger.info("Published {} event for entity: {} with idempotencyKey: {}", eventType, entityId, idempotencyKey);

        } catch (JsonProcessingException e) {
            logger.error("Error publishing compensation event", e);
        }
    }

    public Optional<SagaState> getSagaState(Long orderId) {
        return sagaStateRepository.findByOrderId(orderId);
    }

    @Scheduled(fixedDelay = 5000)
    public void checkTimeouts() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        sagaStateRepository.findAll().stream()
            .filter(saga -> "WAITING".equals(saga.getStatus()))
            .filter(saga -> saga.getTimeoutAt() != null && saga.getTimeoutAt().isBefore(now))
            .forEach(saga -> {
                logger.warn("Saga timeout - no payment received for order: {}", saga.getOrderId());
                saga.setStatus("NO_PAYMENT");
                saga.setCurrentStep("TIMEOUT");
                sagaStateRepository.save(saga);
                
                logSagaEvent(saga.getSagaId(), "SAGA_TIMEOUT", "No payment received within timeout period");
            });
    }

    public void refundPayment(Long orderId) {
        SagaState saga = sagaStateRepository.findByOrderId(orderId).orElseThrow();
        saga.setStatus("REFUNDED");
        saga.setCurrentStep("PAYMENT_REFUNDED");
        sagaStateRepository.save(saga);
        
        logSagaEvent(saga.getSagaId(), "PAYMENT_REFUNDED", "Payment cancelled and refunded");
        
        logger.info("Payment refunded for order: {}", orderId);
    }

    public void failSaga(Long orderId) {
        SagaState saga = sagaStateRepository.findByOrderId(orderId).orElseThrow();
        saga.setStatus("FAILED");
        saga.setCurrentStep("PAYMENT_FAILED");
        sagaStateRepository.save(saga);
        
        logSagaEvent(saga.getSagaId(), "SAGA_FAILED", "Payment failed");
        
        logger.info("Saga failed for order: {}", orderId);
    }
}
