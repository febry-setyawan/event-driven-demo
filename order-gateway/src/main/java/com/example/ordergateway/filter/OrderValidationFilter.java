package com.example.ordergateway.filter;

import com.example.ordergateway.dto.OrderRequest;
import com.example.ordergateway.service.OrderEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class OrderValidationFilter extends AbstractGatewayFilterFactory<OrderValidationFilter.Config> {
    private static final Logger logger = LoggerFactory.getLogger(OrderValidationFilter.class);


    @Autowired
    private Validator validator;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderEventService orderEventService;

    public OrderValidationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            
            if ("POST".equals(request.getMethod().name()) && request.getPath().value().equals("/api/orders")) {
                return DataBufferUtils.join(exchange.getRequest().getBody())
                    .flatMap(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        String body = new String(bytes, StandardCharsets.UTF_8);
                        
                        try {
                            OrderRequest orderRequest = objectMapper.readValue(body, OrderRequest.class);
                            Set<ConstraintViolation<OrderRequest>> violations = validator.validate(orderRequest);
                            
                            if (!violations.isEmpty()) {
                                String errorMessage = violations.iterator().next().getMessage();
                                exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                                exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                                byte[] errorBytes = ("{\"error\":\"" + errorMessage + "\"}").getBytes(StandardCharsets.UTF_8);
                                DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(errorBytes);
                                return exchange.getResponse().writeWith(Mono.just(buffer));
                            }
                            
                            String correlationId = java.util.UUID.randomUUID().toString();
                            orderEventService.publishOrderCreatedAndWait(orderRequest, correlationId);
                            
                            Long orderId = waitForOrderResponse(correlationId);
                            
                            String response;
                            if (orderId != null) {
                                response = String.format("{\"orderId\":%d,\"customerId\":\"%s\",\"productId\":\"%s\",\"quantity\":%d,\"amount\":%.2f,\"status\":\"PENDING\"}",
                                    orderId, orderRequest.getCustomerId(), orderRequest.getProductId(), 
                                    orderRequest.getQuantity(), orderRequest.getAmount());
                            } else {
                                response = "{\"status\":\"PENDING\",\"message\":\"Order is being processed\"}";
                            }
                            
                            exchange.getResponse().setStatusCode(HttpStatus.OK);
                            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
                            DataBuffer responseBuffer = exchange.getResponse().bufferFactory().wrap(responseBytes);
                            return exchange.getResponse().writeWith(Mono.just(responseBuffer));
                            
                        } catch (Exception e) {
                            logger.error("Error processing order request", e);
                            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                            return exchange.getResponse().setComplete();
                        }
                    });
            }
            
            return chain.filter(exchange);
        };
    }

    private final java.util.concurrent.ConcurrentHashMap<String, Long> pendingOrders = new java.util.concurrent.ConcurrentHashMap<>();

    @org.springframework.kafka.annotation.KafkaListener(topics = "order-response", groupId = "gateway-group")
    public void handleOrderResponse(String message) {
        try {
            com.fasterxml.jackson.databind.JsonNode response = objectMapper.readTree(message);
            String correlationId = response.get("correlationId").asText();
            Long orderId = response.get("orderId").asLong();
            pendingOrders.put(correlationId, orderId);
            logger.info("Received order response: orderId={}, correlationId={}", orderId, correlationId);
        } catch (Exception e) {
            logger.error("Error processing order response", e);
        }
    }

    private Long waitForOrderResponse(String correlationId) {
        int attempts = 0;
        while (attempts < 20) {
            Long orderId = pendingOrders.remove(correlationId);
            if (orderId != null) {
                return orderId;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            attempts++;
        }
        return null;
    }

    public static class Config {
    }
}
