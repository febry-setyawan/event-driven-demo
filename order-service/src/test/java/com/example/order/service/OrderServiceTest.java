package com.example.order.service;

import com.example.order.dto.OrderResponse;
import com.example.order.entity.Order;
import com.example.order.entity.SagaState;
import com.example.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private SagaOrchestrator sagaOrchestrator;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderService orderService;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        testOrder = new Order("customer1", "product1", 5, new BigDecimal("100.00"), "PENDING");
        testOrder.setId(1L);
    }

    // ===================== POSITIVE SCENARIOS =====================

    @Test
    void testHandleOrderEvent_OrderCreated_Success() throws Exception {
        // Given
        String orderEventJson = "{\"eventType\":\"OrderCreated\",\"customerId\":\"customer1\"," +
                "\"productId\":\"product1\",\"quantity\":5,\"amount\":\"100.00\"," +
                "\"correlationId\":\"corr123\",\"sagaId\":\"saga123\"}";
        
        com.fasterxml.jackson.databind.JsonNode mockNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode eventTypeNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode customerIdNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode productIdNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode quantityNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode amountNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode correlationIdNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode sagaIdNode = mock(com.fasterxml.jackson.databind.JsonNode.class);

        when(objectMapper.readTree(orderEventJson)).thenReturn(mockNode);
        when(mockNode.get("eventType")).thenReturn(eventTypeNode);
        when(eventTypeNode.asText()).thenReturn("OrderCreated");
        when(mockNode.get("customerId")).thenReturn(customerIdNode);
        when(customerIdNode.asText()).thenReturn("customer1");
        when(mockNode.get("productId")).thenReturn(productIdNode);
        when(productIdNode.asText()).thenReturn("product1");
        when(mockNode.get("quantity")).thenReturn(quantityNode);
        when(quantityNode.asInt()).thenReturn(5);
        when(mockNode.get("amount")).thenReturn(amountNode);
        when(amountNode.asText()).thenReturn("100.00");
        when(mockNode.has("correlationId")).thenReturn(true);
        when(mockNode.get("correlationId")).thenReturn(correlationIdNode);
        when(correlationIdNode.asText()).thenReturn("corr123");
        when(mockNode.has("sagaId")).thenReturn(true);
        when(mockNode.get("sagaId")).thenReturn(sagaIdNode);
        when(sagaIdNode.asText()).thenReturn("saga123");
        
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // When
        orderService.handleOrderEvent(orderEventJson);

        // Then
        verify(orderRepository, times(1)).save(any(Order.class));
        verify(sagaOrchestrator, times(1)).startSagaWithId(eq("saga123"), eq(1L), 
                eq("customer1"), eq("product1"), eq(5), eq(new BigDecimal("100.00")));
    }

    @Test
    void testHandlePaymentEvent_PaymentProcessed_Success() throws Exception {
        // Given
        String paymentEventJson = "{\"eventType\":\"PaymentProcessed\",\"orderId\":1,\"paymentId\":100}";
        
        com.fasterxml.jackson.databind.JsonNode mockNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode eventTypeNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode orderIdNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode paymentIdNode = mock(com.fasterxml.jackson.databind.JsonNode.class);

        when(objectMapper.readTree(paymentEventJson)).thenReturn(mockNode);
        when(mockNode.get("eventType")).thenReturn(eventTypeNode);
        when(eventTypeNode.asText()).thenReturn("PaymentProcessed");
        when(mockNode.get("orderId")).thenReturn(orderIdNode);
        when(orderIdNode.asLong()).thenReturn(1L);
        when(mockNode.get("paymentId")).thenReturn(paymentIdNode);
        when(paymentIdNode.asLong()).thenReturn(100L);
        
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // When
        orderService.handlePaymentEvent(paymentEventJson);

        // Then
        verify(sagaOrchestrator, times(1)).processPayment(1L, 100L);
        verify(sagaOrchestrator, times(1)).completeSaga(1L);
        verify(orderRepository, times(1)).save(argThat(order -> 
            order.getStatus().equals("COMPLETED")
        ));
    }

    @Test
    void testHandlePaymentEvent_PaymentFailed_Success() throws Exception {
        // Given
        String paymentEventJson = "{\"eventType\":\"PaymentFailed\",\"orderId\":1}";
        
        com.fasterxml.jackson.databind.JsonNode mockNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode eventTypeNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode orderIdNode = mock(com.fasterxml.jackson.databind.JsonNode.class);

        when(objectMapper.readTree(paymentEventJson)).thenReturn(mockNode);
        when(mockNode.get("eventType")).thenReturn(eventTypeNode);
        when(eventTypeNode.asText()).thenReturn("PaymentFailed");
        when(mockNode.get("orderId")).thenReturn(orderIdNode);
        when(orderIdNode.asLong()).thenReturn(1L);
        
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // When
        orderService.handlePaymentEvent(paymentEventJson);

        // Then
        verify(sagaOrchestrator, times(1)).failSaga(1L);
        verify(orderRepository, times(1)).save(argThat(order -> 
            order.getStatus().equals("FAILED")
        ));
    }

    @Test
    void testHandlePaymentEvent_PaymentCancelled_Success() throws Exception {
        // Given
        String paymentEventJson = "{\"eventType\":\"PaymentCancelled\",\"orderId\":1}";
        
        com.fasterxml.jackson.databind.JsonNode mockNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode eventTypeNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode orderIdNode = mock(com.fasterxml.jackson.databind.JsonNode.class);

        when(objectMapper.readTree(paymentEventJson)).thenReturn(mockNode);
        when(mockNode.get("eventType")).thenReturn(eventTypeNode);
        when(eventTypeNode.asText()).thenReturn("PaymentCancelled");
        when(mockNode.get("orderId")).thenReturn(orderIdNode);
        when(orderIdNode.asLong()).thenReturn(1L);
        
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // When
        orderService.handlePaymentEvent(paymentEventJson);

        // Then
        verify(sagaOrchestrator, times(1)).refundPayment(1L);
        verify(orderRepository, times(1)).save(argThat(order -> 
            order.getStatus().equals("REFUNDED")
        ));
    }

    @Test
    void testCancelOrder_Success() {
        // Given
        testOrder.setStatus("PENDING");
        SagaState sagaState = new SagaState("saga123", 1L, "WAITING", "ORDER_CREATED");
        
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);
        when(sagaOrchestrator.getSagaState(1L)).thenReturn(Optional.of(sagaState));

        // When
        boolean result = orderService.cancelOrder(1L);

        // Then
        assertTrue(result);
        verify(orderRepository, times(1)).save(argThat(order -> 
            order.getStatus().equals("CANCELLED")
        ));
        verify(sagaOrchestrator, times(1)).compensate(sagaState);
    }

    @Test
    void testGetOrder_Success() {
        // Given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(sagaOrchestrator.getSagaState(1L)).thenReturn(Optional.empty());

        // When
        OrderResponse result = orderService.getOrder(1L);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("customer1", result.getCustomerId());
        assertEquals("product1", result.getProductId());
        assertEquals(5, result.getQuantity());
        assertEquals(new BigDecimal("100.00"), result.getAmount());
    }

    @Test
    void testGetOrder_WithNoPaymentSagaState_UpdatesStatusToFailed() {
        // Given
        SagaState sagaState = new SagaState("saga123", 1L, "NO_PAYMENT", "TIMEOUT");
        
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(sagaOrchestrator.getSagaState(1L)).thenReturn(Optional.of(sagaState));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);

        // When
        OrderResponse result = orderService.getOrder(1L);

        // Then
        assertNotNull(result);
        verify(orderRepository, times(1)).save(argThat(order -> 
            order.getStatus().equals("FAILED")
        ));
    }

    // ===================== NEGATIVE SCENARIOS =====================

    @Test
    void testHandleOrderEvent_InvalidJson_HandlesGracefully() throws Exception {
        // Given
        String invalidJson = "{invalid json}";
        
        when(objectMapper.readTree(invalidJson))
            .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("Invalid JSON") {});

        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> orderService.handleOrderEvent(invalidJson));
    }

    @Test
    void testHandleOrderEvent_UnexpectedException_HandlesGracefully() throws Exception {
        // Given
        String orderEventJson = "{\"eventType\":\"OrderCreated\"}";
        
        when(objectMapper.readTree(orderEventJson))
            .thenThrow(new RuntimeException("Unexpected error"));

        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> orderService.handleOrderEvent(orderEventJson));
    }

    @Test
    void testHandlePaymentEvent_InvalidJson_HandlesGracefully() throws Exception {
        // Given
        String invalidJson = "{invalid json}";
        
        when(objectMapper.readTree(invalidJson))
            .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("Invalid JSON") {});

        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> orderService.handlePaymentEvent(invalidJson));
    }

    @Test
    void testHandlePaymentEvent_OrderNotFound_HandlesGracefully() throws Exception {
        // Given
        String paymentEventJson = "{\"eventType\":\"PaymentProcessed\",\"orderId\":999,\"paymentId\":100}";
        
        com.fasterxml.jackson.databind.JsonNode mockNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode eventTypeNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode orderIdNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode paymentIdNode = mock(com.fasterxml.jackson.databind.JsonNode.class);

        when(objectMapper.readTree(paymentEventJson)).thenReturn(mockNode);
        when(mockNode.get("eventType")).thenReturn(eventTypeNode);
        when(eventTypeNode.asText()).thenReturn("PaymentProcessed");
        when(mockNode.get("orderId")).thenReturn(orderIdNode);
        when(orderIdNode.asLong()).thenReturn(999L);
        when(mockNode.get("paymentId")).thenReturn(paymentIdNode);
        when(paymentIdNode.asLong()).thenReturn(100L);
        
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> orderService.handlePaymentEvent(paymentEventJson));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void testCancelOrder_OrderNotFound_ReturnsFalse() {
        // Given
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        boolean result = orderService.cancelOrder(999L);

        // Then
        assertFalse(result);
        verify(orderRepository, never()).save(any(Order.class));
        verify(sagaOrchestrator, never()).compensate(any(SagaState.class));
    }

    @Test
    void testCancelOrder_OrderAlreadyCompleted_ReturnsFalse() {
        // Given
        testOrder.setStatus("COMPLETED");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        boolean result = orderService.cancelOrder(1L);

        // Then
        assertFalse(result);
        verify(orderRepository, never()).save(any(Order.class));
        verify(sagaOrchestrator, never()).compensate(any(SagaState.class));
    }

    @Test
    void testCancelOrder_OrderAlreadyCancelled_ReturnsFalse() {
        // Given
        testOrder.setStatus("CANCELLED");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        // When
        boolean result = orderService.cancelOrder(1L);

        // Then
        assertFalse(result);
        verify(orderRepository, never()).save(any(Order.class));
        verify(sagaOrchestrator, never()).compensate(any(SagaState.class));
    }

    @Test
    void testCancelOrder_NoSagaState_StillCancelsOrder() {
        // Given
        testOrder.setStatus("PENDING");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(testOrder);
        when(sagaOrchestrator.getSagaState(1L)).thenReturn(Optional.empty());

        // When
        boolean result = orderService.cancelOrder(1L);

        // Then
        assertTrue(result);
        verify(orderRepository, times(1)).save(argThat(order -> 
            order.getStatus().equals("CANCELLED")
        ));
        verify(sagaOrchestrator, never()).compensate(any(SagaState.class));
    }

    @Test
    void testGetOrder_OrderNotFound_ReturnsNull() {
        // Given
        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        OrderResponse result = orderService.getOrder(999L);

        // Then
        assertNull(result);
    }
}
