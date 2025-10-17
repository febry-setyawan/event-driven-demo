package com.example.gateway.controller;

import com.example.gateway.dto.OrderRequest;
import com.example.gateway.dto.OrderResponse;
import com.example.gateway.service.OrderEventService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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

import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders", description = "Order management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private final AtomicLong orderIdGenerator = new AtomicLong(1);

    @Autowired
    private OrderEventService orderEventService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Value("${order.service.url:http://order-service:8081}")
    private String orderServiceUrl;

    private final WebClient webClient = WebClient.builder().build();

    @PostMapping
    @Operation(summary = "Create order", description = "Create a new order and publish event to Kafka")
    public ResponseEntity<OrderResponse> createOrder(@RequestBody OrderRequest request) {
        logger.info("Creating order for customer: {}", request.getCustomerId());
        
        Long orderId = orderIdGenerator.getAndIncrement();
        
        // Publish event to Kafka
        orderEventService.publishOrderCreated(orderId, request);
        
        OrderResponse response = new OrderResponse(
            orderId,
            request.getCustomerId(),
            request.getProductId(),
            request.getQuantity(),
            request.getAmount(),
            "PENDING"
        );
        
        logger.info("Order created with ID: {}", orderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order", description = "Retrieve order details by ID")
    public Mono<ResponseEntity<OrderResponse>> getOrder(@PathVariable Long orderId) {
        logger.info("Getting order: {}", orderId);
        io.github.resilience4j.circuitbreaker.CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("orderService");
        
        return webClient.get()
                .uri(orderServiceUrl + "/orders/" + orderId)
                .retrieve()
                .bodyToMono(OrderResponse.class)
                .timeout(java.time.Duration.ofSeconds(5))
                .transformDeferred(CircuitBreakerOperator.of(cb))
                .map(ResponseEntity::ok)
                .onErrorResume(ex -> {
                    logger.error("Circuit breaker fallback for order: {}, error: {}", orderId, ex.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
                });
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("API Gateway is healthy");
    }
}
