package com.example.payment.service;

import com.example.payment.dto.PaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.entity.Payment;
import com.example.payment.repository.PaymentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);
    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    public PaymentResponse processPayment(PaymentRequest request) {
        logger.info("Processing payment for order: {} amount: {}", request.getOrderId(), request.getAmount());

        // Check for existing payment (idempotency)
        Optional<Payment> existingPayment = paymentRepository.findByOrderId(request.getOrderId());
        if (existingPayment.isPresent()) {
            logger.info("Payment already exists for order: {}, returning existing payment", request.getOrderId());
            Payment p = existingPayment.get();
            return new PaymentResponse(p.getId(), p.getOrderId(), p.getAmount(), p.getStatus(), p.getProcessedAt());
        }

        // Simulate payment failure for amount < 10
        if (request.getAmount().doubleValue() < 10.0) {
            logger.error("Payment failed for order: {} - amount too low", request.getOrderId());
            publishPaymentFailedEvent(request.getOrderId());
            throw new RuntimeException("Payment failed: amount too low");
        }

        // Retry logic for database save
        int maxRetries = 3;
        int attempt = 0;
        Payment payment = null;
        boolean simulateError = request.getAmount().doubleValue() == 999.99;
        
        while (attempt < maxRetries) {
            try {
                attempt++;
                logger.info("Attempting to save payment (attempt {}/{}) for order: {}", attempt, maxRetries, request.getOrderId());
                
                // Simulate database error for amount = 999.99
                if (simulateError) {
                    logger.error("Simulating database error (attempt {}/{})", attempt, maxRetries);
                    throw new RuntimeException("Simulated database connection error");
                }
                
                payment = new Payment(request.getOrderId(), request.getAmount(), "COMPLETED");
                payment = paymentRepository.save(payment);
                
                logger.info("Payment saved with ID: {} (attempt: {})", payment.getId(), attempt);
                break;
                
            } catch (Exception e) {
                logger.error("Error saving payment (attempt {}/{}): {}", attempt, maxRetries, e.getMessage());
                
                if (attempt >= maxRetries) {
                    logger.error("Failed to save payment after {} attempts for order: {}", maxRetries, request.getOrderId());
                    publishPaymentFailedEvent(request.getOrderId());
                    throw new RuntimeException("Payment processing failed after " + maxRetries + " attempts", e);
                }
                
                try {
                    Thread.sleep(1000 * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        // Publish payment event with retry
        publishPaymentProcessedWithRetry(payment);

        return new PaymentResponse(
            payment.getId(),
            payment.getOrderId(),
            payment.getAmount(),
            payment.getStatus(),
            payment.getProcessedAt()
        );
    }

    public PaymentResponse getPayment(Long paymentId) {
        Optional<Payment> payment = paymentRepository.findById(paymentId);
        
        if (payment.isPresent()) {
            Payment p = payment.get();
            return new PaymentResponse(p.getId(), p.getOrderId(), p.getAmount(), p.getStatus(), p.getProcessedAt());
        }
        
        return null;
    }

    public boolean cancelPayment(Long paymentId) {
        Optional<Payment> payment = paymentRepository.findById(paymentId);
        
        if (payment.isPresent()) {
            Payment p = payment.get();
            p.setStatus("CANCELLED");
            paymentRepository.save(p);
            
            publishPaymentCancelledEvent(p);
            logger.info("Payment cancelled: {}", paymentId);
            return true;
        }
        
        return false;
    }

    private void publishPaymentFailedEvent(Long orderId) {
        try {
            String idempotencyKey = UUID.randomUUID().toString();
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "PaymentFailed");
            event.put("orderId", orderId);
            event.put("idempotencyKey", idempotencyKey);
            event.put("timestamp", Instant.now().toString());

            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, orderId.toString(), eventJson);
            
            logger.info("Published PaymentFailed event for order: {} with idempotencyKey: {}", orderId, idempotencyKey);
        } catch (JsonProcessingException e) {
            logger.error("Error publishing payment failed event", e);
        }
    }

    private void publishPaymentCancelledEvent(Payment payment) {
        try {
            String idempotencyKey = UUID.randomUUID().toString();
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "PaymentCancelled");
            event.put("paymentId", payment.getId());
            event.put("orderId", payment.getOrderId());
            event.put("idempotencyKey", idempotencyKey);
            event.put("timestamp", Instant.now().toString());

            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, payment.getId().toString(), eventJson);
            
            logger.info("Published PaymentCancelled event for payment: {} with idempotencyKey: {}", payment.getId(), idempotencyKey);
        } catch (JsonProcessingException e) {
            logger.error("Error publishing payment cancelled event", e);
        }
    }

    private void publishPaymentProcessedWithRetry(Payment payment) {
        int maxRetries = 3;
        int attempt = 0;
        
        while (attempt < maxRetries) {
            try {
                String idempotencyKey = UUID.randomUUID().toString();
                Map<String, Object> event = new HashMap<>();
                event.put("eventType", "PaymentProcessed");
                event.put("paymentId", payment.getId());
                event.put("orderId", payment.getOrderId());
                event.put("amount", payment.getAmount());
                event.put("status", payment.getStatus());
                event.put("idempotencyKey", idempotencyKey);
                event.put("timestamp", Instant.now().toString());

                String eventJson = objectMapper.writeValueAsString(event);
                
                kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, payment.getId().toString(), eventJson).get();
                logger.info("Published PaymentProcessed event for payment: {} with idempotencyKey: {} (attempt: {})", payment.getId(), idempotencyKey, attempt + 1);
                return;
                
            } catch (Exception e) {
                attempt++;
                logger.error("Error publishing payment event (attempt {}/{}): {}", attempt, maxRetries, e.getMessage());
                
                if (attempt >= maxRetries) {
                    logger.error("Failed to publish payment event after {} attempts for payment: {}", maxRetries, payment.getId());
                    // In production: send to dead letter queue or alert monitoring
                } else {
                    try {
                        Thread.sleep(1000 * attempt); // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }
}
