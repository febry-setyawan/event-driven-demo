package com.example.ordergateway.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class FallbackController {

    @GetMapping("/api/orders/health")
    public ResponseEntity<String> ordersHealth() {
        return ResponseEntity.ok("Order Gateway is healthy");
    }

    @GetMapping("/fallback/orders")
    @PostMapping("/fallback/orders")
    public Mono<ResponseEntity<Map<String, String>>> orderFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("error", "Order service is currently unavailable. Please try again later.")));
    }

    @GetMapping("/fallback/payments")
    @PostMapping("/fallback/payments")
    public Mono<ResponseEntity<Map<String, String>>> paymentFallback() {
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("error", "Payment service is currently unavailable. Please try again later.")));
    }
}
