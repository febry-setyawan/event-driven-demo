package com.example.order.controller;

import com.example.order.dto.OrderResponse;
import com.example.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    @InjectMocks
    private OrderController orderController;

    private OrderResponse testOrderResponse;

    @BeforeEach
    void setUp() {
        testOrderResponse = new OrderResponse(1L, "customer1", "product1", 5, new BigDecimal("100.00"), "COMPLETED");
    }

    // ===================== POSITIVE SCENARIOS =====================

    @Test
    void testGetOrder_Found_ReturnsOk() {
        // Given
        when(orderService.getOrder(1L)).thenReturn(testOrderResponse);

        // When
        ResponseEntity<OrderResponse> response = orderController.getOrder(1L);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1L, response.getBody().getId());
        assertEquals("customer1", response.getBody().getCustomerId());
        assertEquals("product1", response.getBody().getProductId());
        assertEquals(5, response.getBody().getQuantity());
        assertEquals(new BigDecimal("100.00"), response.getBody().getAmount());
        assertEquals("COMPLETED", response.getBody().getStatus());
        verify(orderService, times(1)).getOrder(1L);
    }

    @Test
    void testCancelOrder_Success_ReturnsOk() {
        // Given
        when(orderService.cancelOrder(1L)).thenReturn(true);

        // When
        ResponseEntity<String> response = orderController.cancelOrder(1L);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Order cancelled successfully", response.getBody());
        verify(orderService, times(1)).cancelOrder(1L);
    }

    @Test
    void testHealth_ReturnsHealthy() {
        // When
        ResponseEntity<String> response = orderController.health();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Order Service is healthy", response.getBody());
    }

    // ===================== NEGATIVE SCENARIOS =====================

    @Test
    void testGetOrder_NotFound_ReturnsNotFound() {
        // Given
        when(orderService.getOrder(999L)).thenReturn(null);

        // When
        ResponseEntity<OrderResponse> response = orderController.getOrder(999L);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
        verify(orderService, times(1)).getOrder(999L);
    }

    @Test
    void testCancelOrder_NotFound_ReturnsNotFound() {
        // Given
        when(orderService.cancelOrder(999L)).thenReturn(false);

        // When
        ResponseEntity<String> response = orderController.cancelOrder(999L);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(orderService, times(1)).cancelOrder(999L);
    }

    @Test
    void testGetOrder_ServiceThrowsException_PropagatesException() {
        // Given
        when(orderService.getOrder(1L)).thenThrow(new RuntimeException("Database error"));

        // When/Then
        assertThrows(RuntimeException.class, () -> orderController.getOrder(1L));
        verify(orderService, times(1)).getOrder(1L);
    }

    @Test
    void testCancelOrder_ServiceThrowsException_PropagatesException() {
        // Given
        when(orderService.cancelOrder(1L)).thenThrow(new RuntimeException("Database error"));

        // When/Then
        assertThrows(RuntimeException.class, () -> orderController.cancelOrder(1L));
        verify(orderService, times(1)).cancelOrder(1L);
    }

    @Test
    void testGetOrder_WithDifferentStatuses_ReturnsCorrectly() {
        // Test various order statuses
        String[] statuses = {"PENDING", "COMPLETED", "FAILED", "CANCELLED", "REFUNDED"};
        
        for (String status : statuses) {
            // Given
            OrderResponse orderWithStatus = new OrderResponse(1L, "customer1", "product1", 5, new BigDecimal("100.00"), status);
            when(orderService.getOrder(1L)).thenReturn(orderWithStatus);

            // When
            ResponseEntity<OrderResponse> response = orderController.getOrder(1L);

            // Then
            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(status, response.getBody().getStatus());
        }
    }

    @Test
    void testCancelOrder_MultipleOrders_IndependentResults() {
        // Given
        when(orderService.cancelOrder(1L)).thenReturn(true);
        when(orderService.cancelOrder(2L)).thenReturn(false);
        when(orderService.cancelOrder(3L)).thenReturn(true);

        // When
        ResponseEntity<String> response1 = orderController.cancelOrder(1L);
        ResponseEntity<String> response2 = orderController.cancelOrder(2L);
        ResponseEntity<String> response3 = orderController.cancelOrder(3L);

        // Then
        assertEquals(HttpStatus.OK, response1.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, response2.getStatusCode());
        assertEquals(HttpStatus.OK, response3.getStatusCode());
        verify(orderService, times(1)).cancelOrder(1L);
        verify(orderService, times(1)).cancelOrder(2L);
        verify(orderService, times(1)).cancelOrder(3L);
    }
}
