package com.example.gateway.controller;

import com.example.gateway.dto.OrderRequest;
import com.example.gateway.dto.OrderResponse;
import com.example.gateway.service.OrderEventService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger logger = LoggerFactory.getLogger(OrderController.class);
    private final AtomicLong orderIdGenerator = new AtomicLong(1);

    @Autowired
    private OrderEventService orderEventService;

    @Value("${order.service.url:http://order-service:8081}")
    private String orderServiceUrl;

    private final WebClient webClient = WebClient.builder().build();

    @PostMapping
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
    public Mono<ResponseEntity<OrderResponse>> getOrder(@PathVariable Long orderId) {
        logger.info("Getting order: {}", orderId);
        
        return webClient.get()
                .uri(orderServiceUrl + "/orders/" + orderId)
                .retrieve()
                .bodyToMono(OrderResponse.class)
                .map(ResponseEntity::ok)
                .onErrorReturn(ResponseEntity.notFound().build());
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("API Gateway is healthy");
    }
}
