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

        // Simulate payment processing
        String status = "COMPLETED";
        
        try {
            // Save to database
            Payment payment = new Payment(request.getOrderId(), request.getAmount(), status);
            payment = paymentRepository.save(payment);

            logger.info("Payment saved with ID: {}", payment.getId());

            // Publish payment event with retry
            publishPaymentProcessedWithRetry(payment);

            return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getProcessedAt()
            );
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Race condition: another thread created payment, fetch and return it
            logger.warn("Duplicate payment detected for order: {}, fetching existing payment", request.getOrderId());
            Payment p = paymentRepository.findByOrderId(request.getOrderId())
                .orElseThrow(() -> new RuntimeException("Payment not found after duplicate key error"));
            return new PaymentResponse(p.getId(), p.getOrderId(), p.getAmount(), p.getStatus(), p.getProcessedAt());
        }
    }

    public PaymentResponse getPayment(Long paymentId) {
        Optional<Payment> payment = paymentRepository.findById(paymentId);
        
        if (payment.isPresent()) {
            Payment p = payment.get();
            return new PaymentResponse(p.getId(), p.getOrderId(), p.getAmount(), p.getStatus(), p.getProcessedAt());
        }
        
        return null;
    }

    private void publishPaymentProcessedWithRetry(Payment payment) {
        int maxRetries = 3;
        int attempt = 0;
        
        while (attempt < maxRetries) {
            try {
                Map<String, Object> event = new HashMap<>();
                event.put("eventType", "PaymentProcessed");
                event.put("paymentId", payment.getId());
                event.put("orderId", payment.getOrderId());
                event.put("amount", payment.getAmount());
                event.put("status", payment.getStatus());
                event.put("timestamp", Instant.now().toString());

                String eventJson = objectMapper.writeValueAsString(event);
                
                kafkaTemplate.send(PAYMENT_EVENTS_TOPIC, payment.getId().toString(), eventJson).get();
                logger.info("Published PaymentProcessed event for payment: {} (attempt: {})", payment.getId(), attempt + 1);
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
