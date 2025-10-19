package com.example.ordergateway.service;

import com.example.ordergateway.dto.OrderRequest;
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

@Service
public class OrderEventService {

    private static final Logger logger = LoggerFactory.getLogger(OrderEventService.class);
    private static final String ORDER_EVENTS_TOPIC = "order-events";

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    public Long publishOrderCreatedAndWait(OrderRequest request, String correlationId) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "OrderCreated");
            event.put("customerId", request.getCustomerId());
            event.put("productId", request.getProductId());
            event.put("quantity", request.getQuantity());
            event.put("amount", request.getAmount());
            event.put("correlationId", correlationId);
            event.put("timestamp", Instant.now().toString());

            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(ORDER_EVENTS_TOPIC, request.getCustomerId(), eventJson).get();
            logger.info("Published OrderCreated event with correlationId: {}", correlationId);
            
            Thread.sleep(500);
            return null;
        } catch (Exception e) {
            logger.error("Error publishing order event", e);
            return null;
        }
    }
}
