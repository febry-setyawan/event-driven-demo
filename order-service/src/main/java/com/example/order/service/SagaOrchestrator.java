package com.example.order.service;

import com.example.order.dto.PaymentRequest;
import com.example.order.entity.SagaState;
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

@Service
public class SagaOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(SagaOrchestrator.class);
    private static final String COMPENSATION_TOPIC = "compensation-events";

    @Autowired
    private SagaStateRepository sagaStateRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${payment.service.url:http://payment-service:8082}")
    private String paymentServiceUrl;

    private final WebClient webClient = WebClient.builder().build();

    public void startSaga(Long orderId, String customerId, String productId, Integer quantity, BigDecimal amount) {
        logger.info("Starting saga for order: {}", orderId);

        SagaState saga = new SagaState(orderId, "STARTED", "ORDER_CREATED");
        saga = sagaStateRepository.save(saga);
        logger.info("Saga state saved for order: {}", orderId);

        try {
            processPaymentStep(orderId, amount);
        } catch (Exception e) {
            logger.error("Error in saga execution for order: {}, initiating compensation", orderId, e);
            compensate(saga);
        }
    }

    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentServiceFallback")
    private void processPaymentStep(Long orderId, BigDecimal amount) throws JsonProcessingException {
        SagaState saga = sagaStateRepository.findByOrderId(orderId).orElseThrow();
        saga.setCurrentStep("PAYMENT_PENDING");
        saga.setStatus("PROCESSING");
        saga = sagaStateRepository.save(saga);
        logger.info("Saga step: Processing payment for order: {}", orderId);

        PaymentRequest paymentRequest = new PaymentRequest(orderId, amount);
        String response = webClient.post()
                .uri(paymentServiceUrl + "/api/payments")
                .bodyValue(paymentRequest)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode paymentResponse = objectMapper.readTree(response);
        Long paymentId = paymentResponse.get("id").asLong();

        saga.setPaymentId(paymentId);
        saga.setCurrentStep("PAYMENT_COMPLETED");
        saga.setStatus("COMPLETED");
        sagaStateRepository.save(saga);

        logger.info("Saga completed successfully for order: {}", orderId);
    }

    private void paymentServiceFallback(Long orderId, BigDecimal amount, Exception ex) {
        logger.error("Circuit breaker triggered for order: {}, initiating compensation", orderId);
        SagaState saga = sagaStateRepository.findByOrderId(orderId).orElse(null);
        if (saga != null) {
            compensate(saga);
        }
    }

    private void compensate(SagaState saga) {
        saga.setStatus("COMPENSATING");
        sagaStateRepository.save(saga);

        logger.info("Starting compensation for order: {}", saga.getOrderId());

        if (saga.getPaymentId() != null) {
            cancelPayment(saga.getPaymentId());
        }

        cancelOrder(saga.getOrderId());

        saga.setStatus("FAILED");
        saga.setCurrentStep("COMPENSATED");
        sagaStateRepository.save(saga);

        logger.info("Compensation completed for order: {}", saga.getOrderId());
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

    private void publishCompensationEvent(String eventType, Long entityId) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", eventType);
            event.put("entityId", entityId);
            event.put("timestamp", Instant.now().toString());

            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(COMPENSATION_TOPIC, entityId.toString(), eventJson);

            logger.info("Published {} event for entity: {}", eventType, entityId);

        } catch (JsonProcessingException e) {
            logger.error("Error publishing compensation event", e);
        }
    }

    public Optional<SagaState> getSagaState(Long orderId) {
        return sagaStateRepository.findByOrderId(orderId);
    }

    @Scheduled(fixedDelay = 5000)
    public void checkTimeouts() {
        sagaStateRepository.findAll().stream()
            .filter(saga -> "PROCESSING".equals(saga.getStatus()) || "STARTED".equals(saga.getStatus()))
            .filter(saga -> saga.getTimeoutAt() != null && saga.getTimeoutAt().isBefore(java.time.LocalDateTime.now()))
            .forEach(saga -> {
                logger.warn("Saga timeout detected for order: {}", saga.getOrderId());
                compensate(saga);
            });
    }

    @PostConstruct
    public void recoverSagas() {
        logger.info("Checking for in-progress sagas to recover...");
        sagaStateRepository.findAll().stream()
            .filter(saga -> "PROCESSING".equals(saga.getStatus()) || "STARTED".equals(saga.getStatus()))
            .filter(saga -> saga.getCreatedAt().isBefore(java.time.LocalDateTime.now().minusMinutes(1)))
            .forEach(saga -> {
                logger.info("Recovering saga for order: {} with status: {}, created at: {}", saga.getOrderId(), saga.getStatus(), saga.getCreatedAt());
                compensate(saga);
            });
    }
}
