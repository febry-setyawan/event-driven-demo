package com.example.gateway.service;

import com.example.gateway.dto.OrderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderEventServiceTest {

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderEventService orderEventService;

    private OrderRequest testOrderRequest;

    @BeforeEach
    void setUp() {
        testOrderRequest = new OrderRequest();
        testOrderRequest.setCustomerId("customer1");
        testOrderRequest.setProductId("product1");
        testOrderRequest.setQuantity(5);
        testOrderRequest.setAmount(new BigDecimal("100.00"));
    }

    // ===================== POSITIVE SCENARIOS =====================

    @Test
    void testPublishOrderCreated_Success() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{\"eventType\":\"OrderCreated\"}");

        // When
        orderEventService.publishOrderCreated(1L, testOrderRequest);

        // Then
        verify(objectMapper, times(1)).writeValueAsString(anyMap());
        verify(kafkaTemplate, times(1)).send(eq("order-events"), eq("1"), anyString());
    }

    @Test
    void testPublishOrderCreated_WithDifferentData() throws Exception {
        // Given
        OrderRequest customRequest = new OrderRequest();
        customRequest.setCustomerId("customer123");
        customRequest.setProductId("product456");
        customRequest.setQuantity(10);
        customRequest.setAmount(new BigDecimal("500.00"));
        
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

        // When
        orderEventService.publishOrderCreated(100L, customRequest);

        // Then
        verify(kafkaTemplate, times(1)).send(eq("order-events"), eq("100"), anyString());
    }

    @Test
    void testPublishOrderCreated_MultipleOrders() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

        // When
        orderEventService.publishOrderCreated(1L, testOrderRequest);
        orderEventService.publishOrderCreated(2L, testOrderRequest);
        orderEventService.publishOrderCreated(3L, testOrderRequest);

        // Then
        verify(kafkaTemplate, times(3)).send(anyString(), anyString(), anyString());
    }

    // ===================== NEGATIVE SCENARIOS =====================

    @Test
    void testPublishOrderCreated_JsonProcessingException_HandlesGracefully() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(anyMap()))
            .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("JSON error") {});

        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> orderEventService.publishOrderCreated(1L, testOrderRequest));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void testPublishOrderCreated_KafkaException_PropagatesException() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("Kafka error"));

        // When/Then
        assertThrows(RuntimeException.class, 
            () -> orderEventService.publishOrderCreated(1L, testOrderRequest));
    }

    @Test
    void testPublishOrderCreated_NullOrderId_HandlesGracefully() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

        // When/Then - Should not throw NullPointerException
        assertDoesNotThrow(() -> orderEventService.publishOrderCreated(null, testOrderRequest));
    }

    @Test
    void testPublishOrderCreated_NullRequest_HandlesGracefully() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

        // When/Then - Should not throw NullPointerException
        assertDoesNotThrow(() -> orderEventService.publishOrderCreated(1L, null));
    }

    @Test
    void testPublishOrderCreated_ZeroAmount_PublishesSuccessfully() throws Exception {
        // Given
        testOrderRequest.setAmount(BigDecimal.ZERO);
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

        // When
        orderEventService.publishOrderCreated(1L, testOrderRequest);

        // Then
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void testPublishOrderCreated_NegativeAmount_PublishesSuccessfully() throws Exception {
        // Given
        testOrderRequest.setAmount(new BigDecimal("-100.00"));
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

        // When
        orderEventService.publishOrderCreated(1L, testOrderRequest);

        // Then
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void testPublishOrderCreated_ZeroQuantity_PublishesSuccessfully() throws Exception {
        // Given
        testOrderRequest.setQuantity(0);
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

        // When
        orderEventService.publishOrderCreated(1L, testOrderRequest);

        // Then
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void testPublishOrderCreated_NullCustomerId_PublishesSuccessfully() throws Exception {
        // Given
        testOrderRequest.setCustomerId(null);
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

        // When
        orderEventService.publishOrderCreated(1L, testOrderRequest);

        // Then
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void testPublishOrderCreated_EmptyStrings_PublishesSuccessfully() throws Exception {
        // Given
        testOrderRequest.setCustomerId("");
        testOrderRequest.setProductId("");
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

        // When
        orderEventService.publishOrderCreated(1L, testOrderRequest);

        // Then
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
    }
}
