package com.example.payment.controller;

import com.example.payment.dto.PaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    private PaymentRequest testRequest;
    private PaymentResponse testResponse;

    @BeforeEach
    void setUp() {
        testRequest = new PaymentRequest();
        testRequest.setOrderId(1L);
        testRequest.setAmount(new BigDecimal("100.00"));
        
        testResponse = new PaymentResponse(100L, 1L, new BigDecimal("100.00"), "COMPLETED", LocalDateTime.now());
    }

    // ===================== POSITIVE SCENARIOS =====================

    @Test
    void testProcessPayment_Success_ReturnsOk() {
        // Given
        when(paymentService.processPayment(testRequest)).thenReturn(testResponse);

        // When
        ResponseEntity<PaymentResponse> response = paymentController.processPayment(testRequest);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(100L, response.getBody().getId());
        assertEquals(1L, response.getBody().getOrderId());
        assertEquals(new BigDecimal("100.00"), response.getBody().getAmount());
        assertEquals("COMPLETED", response.getBody().getStatus());
        verify(paymentService, times(1)).processPayment(testRequest);
    }

    @Test
    void testGetPayment_Found_ReturnsOk() {
        // Given
        when(paymentService.getPayment(100L)).thenReturn(testResponse);

        // When
        ResponseEntity<PaymentResponse> response = paymentController.getPayment(100L);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(100L, response.getBody().getId());
        assertEquals(1L, response.getBody().getOrderId());
        verify(paymentService, times(1)).getPayment(100L);
    }

    @Test
    void testCancelPayment_Success_ReturnsOk() {
        // Given
        when(paymentService.cancelPayment(100L)).thenReturn(true);

        // When
        ResponseEntity<String> response = paymentController.cancelPayment(100L);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Payment cancelled", response.getBody());
        verify(paymentService, times(1)).cancelPayment(100L);
    }

    @Test
    void testHealth_ReturnsHealthy() {
        // When
        ResponseEntity<String> response = paymentController.health();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Payment Service is healthy", response.getBody());
    }

    @Test
    void testProcessPayment_DifferentAmounts_HandlesCorrectly() {
        // Test with various amounts
        BigDecimal[] amounts = {
            new BigDecimal("10.00"),
            new BigDecimal("50.50"),
            new BigDecimal("999.99"),
            new BigDecimal("1000.00")
        };

        for (BigDecimal amount : amounts) {
            // Given
            testRequest.setAmount(amount);
            PaymentResponse response = new PaymentResponse(100L, 1L, amount, "COMPLETED", LocalDateTime.now());
            when(paymentService.processPayment(testRequest)).thenReturn(response);

            // When
            ResponseEntity<PaymentResponse> result = paymentController.processPayment(testRequest);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals(amount, result.getBody().getAmount());
        }
    }

    // ===================== NEGATIVE SCENARIOS =====================

    @Test
    void testGetPayment_NotFound_ReturnsNotFound() {
        // Given
        when(paymentService.getPayment(999L)).thenReturn(null);

        // When
        ResponseEntity<PaymentResponse> response = paymentController.getPayment(999L);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
        verify(paymentService, times(1)).getPayment(999L);
    }

    @Test
    void testCancelPayment_NotFound_ReturnsNotFound() {
        // Given
        when(paymentService.cancelPayment(999L)).thenReturn(false);

        // When
        ResponseEntity<String> response = paymentController.cancelPayment(999L);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(paymentService, times(1)).cancelPayment(999L);
    }

    @Test
    void testProcessPayment_ServiceThrowsException_PropagatesException() {
        // Given
        when(paymentService.processPayment(testRequest))
            .thenThrow(new RuntimeException("Payment processing failed"));

        // When/Then
        assertThrows(RuntimeException.class, () -> paymentController.processPayment(testRequest));
        verify(paymentService, times(1)).processPayment(testRequest);
    }

    @Test
    void testGetPayment_ServiceThrowsException_PropagatesException() {
        // Given
        when(paymentService.getPayment(100L))
            .thenThrow(new RuntimeException("Database error"));

        // When/Then
        assertThrows(RuntimeException.class, () -> paymentController.getPayment(100L));
        verify(paymentService, times(1)).getPayment(100L);
    }

    @Test
    void testCancelPayment_ServiceThrowsException_PropagatesException() {
        // Given
        when(paymentService.cancelPayment(100L))
            .thenThrow(new RuntimeException("Cancellation failed"));

        // When/Then
        assertThrows(RuntimeException.class, () -> paymentController.cancelPayment(100L));
        verify(paymentService, times(1)).cancelPayment(100L);
    }

    @Test
    void testProcessPayment_NullRequest_PropagatesException() {
        // When/Then
        assertThrows(Exception.class, () -> paymentController.processPayment(null));
    }

    @Test
    void testProcessPayment_InvalidRequest_PropagatesException() {
        // Given
        PaymentRequest invalidRequest = new PaymentRequest();
        // Missing required fields
        when(paymentService.processPayment(invalidRequest))
            .thenThrow(new RuntimeException("Invalid request"));

        // When/Then
        assertThrows(RuntimeException.class, () -> paymentController.processPayment(invalidRequest));
    }

    @Test
    void testGetPayment_NegativeId_HandlesGracefully() {
        // Given
        when(paymentService.getPayment(-1L)).thenReturn(null);

        // When
        ResponseEntity<PaymentResponse> response = paymentController.getPayment(-1L);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(paymentService, times(1)).getPayment(-1L);
    }

    @Test
    void testCancelPayment_NegativeId_HandlesGracefully() {
        // Given
        when(paymentService.cancelPayment(-1L)).thenReturn(false);

        // When
        ResponseEntity<String> response = paymentController.cancelPayment(-1L);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(paymentService, times(1)).cancelPayment(-1L);
    }

    @Test
    void testMultiplePayments_IndependentResults() {
        // Given
        PaymentRequest request1 = new PaymentRequest();
        request1.setOrderId(1L);
        request1.setAmount(new BigDecimal("100.00"));
        
        PaymentRequest request2 = new PaymentRequest();
        request2.setOrderId(2L);
        request2.setAmount(new BigDecimal("200.00"));
        
        PaymentResponse response1 = new PaymentResponse(100L, 1L, new BigDecimal("100.00"), "COMPLETED", LocalDateTime.now());
        PaymentResponse response2 = new PaymentResponse(101L, 2L, new BigDecimal("200.00"), "COMPLETED", LocalDateTime.now());
        
        when(paymentService.processPayment(request1)).thenReturn(response1);
        when(paymentService.processPayment(request2)).thenReturn(response2);

        // When
        ResponseEntity<PaymentResponse> result1 = paymentController.processPayment(request1);
        ResponseEntity<PaymentResponse> result2 = paymentController.processPayment(request2);

        // Then
        assertEquals(HttpStatus.OK, result1.getStatusCode());
        assertEquals(HttpStatus.OK, result2.getStatusCode());
        assertEquals(100L, result1.getBody().getId());
        assertEquals(101L, result2.getBody().getId());
        verify(paymentService, times(1)).processPayment(request1);
        verify(paymentService, times(1)).processPayment(request2);
    }
}
