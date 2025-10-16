package com.example.gateway.controller;

import com.example.gateway.dto.OrderRequest;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Payment management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class PaymentController {
    private static final Logger logger = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Value("${payment.service.url:http://payment-service:8082}")
    private String paymentServiceUrl;

    private final WebClient webClient = WebClient.builder().build();

    @GetMapping("/{paymentId}")
    @Operation(summary = "Get payment", description = "Retrieve payment details by ID")
    public Mono<ResponseEntity<Object>> getPayment(@PathVariable Long paymentId) {
        logger.info("Getting payment: {}", paymentId);
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentService");
        
        return webClient.get()
                .uri(paymentServiceUrl + "/payments/" + paymentId)
                .retrieve()
                .bodyToMono(Object.class)
                .timeout(java.time.Duration.ofSeconds(5))
                .transformDeferred(CircuitBreakerOperator.of(cb))
                .map(ResponseEntity::ok)
                .onErrorResume(ex -> {
                    logger.error("Circuit breaker fallback for payment: {}, error: {}", paymentId, ex.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
                });
    }
}
