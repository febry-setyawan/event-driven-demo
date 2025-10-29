package com.example.payment.service;

import com.example.payment.dto.PaymentRequest;
import com.example.payment.dto.PaymentResponse;
import com.example.payment.entity.Payment;
import com.example.payment.repository.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private PaymentService paymentService;

    private Payment testPayment;
    private PaymentRequest testRequest;

    @BeforeEach
    void setUp() {
        testPayment = new Payment(1L, new BigDecimal("100.00"), "COMPLETED");
        testPayment.setId(100L);
        
        testRequest = new PaymentRequest();
        testRequest.setOrderId(1L);
        testRequest.setAmount(new BigDecimal("100.00"));
    }

    // ===================== POSITIVE SCENARIOS =====================

    @Test
    void testProcessPayment_Success() throws Exception {
        // Given
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // When
        PaymentResponse response = paymentService.processPayment(testRequest);

        // Then
        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(1L, response.getOrderId());
        assertEquals(new BigDecimal("100.00"), response.getAmount());
        assertEquals("COMPLETED", response.getStatus());
        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(kafkaTemplate, atLeastOnce()).send(anyString(), anyString(), anyString());
    }

    @Test
    void testProcessPayment_Idempotent_ReturnsExisting() throws Exception {
        // Given
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(testPayment));

        // When
        PaymentResponse response = paymentService.processPayment(testRequest);

        // Then
        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(1L, response.getOrderId());
        assertEquals(new BigDecimal("100.00"), response.getAmount());
        assertEquals("COMPLETED", response.getStatus());
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void testProcessPayment_HighAmount_Success() throws Exception {
        // Given
        testRequest.setAmount(new BigDecimal("999.99"));
        testPayment = new Payment(1L, new BigDecimal("999.99"), "COMPLETED");
        testPayment.setId(100L);
        
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // When
        PaymentResponse response = paymentService.processPayment(testRequest);

        // Then
        assertNotNull(response);
        assertEquals(new BigDecimal("999.99"), response.getAmount());
        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    void testGetPayment_Found_ReturnsPayment() {
        // Given
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(testPayment));

        // When
        PaymentResponse response = paymentService.getPayment(100L);

        // Then
        assertNotNull(response);
        assertEquals(100L, response.getId());
        assertEquals(1L, response.getOrderId());
        assertEquals(new BigDecimal("100.00"), response.getAmount());
        assertEquals("COMPLETED", response.getStatus());
        verify(paymentRepository, times(1)).findById(100L);
    }

    @Test
    void testCancelPayment_Success() throws Exception {
        // Given
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(testPayment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

        // When
        boolean result = paymentService.cancelPayment(100L);

        // Then
        assertTrue(result);
        verify(paymentRepository, times(1)).save(argThat(payment -> 
            payment.getStatus().equals("CANCELLED")
        ));
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void testProcessPayment_KafkaPublishRetry_EventuallySucceeds() throws Exception {
        // Given
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");
        
        // First attempt fails, second succeeds
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka error")))
            .thenReturn(CompletableFuture.completedFuture(null));

        // When
        PaymentResponse response = paymentService.processPayment(testRequest);

        // Then
        assertNotNull(response);
        verify(kafkaTemplate, atLeast(2)).send(anyString(), anyString(), anyString());
    }

    // ===================== NEGATIVE SCENARIOS =====================

    @Test
    void testProcessPayment_AmountTooLow_ThrowsException() throws Exception {
        // Given
        testRequest.setAmount(new BigDecimal("5.00"));
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

        // When/Then
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> paymentService.processPayment(testRequest));
        
        assertTrue(exception.getMessage().contains("amount too low"));
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString()); // PaymentFailed event
    }

    @Test
    void testProcessPayment_AmountExactlyTen_Success() throws Exception {
        // Given
        testRequest.setAmount(new BigDecimal("10.00"));
        testPayment = new Payment(1L, new BigDecimal("10.00"), "COMPLETED");
        testPayment.setId(100L);
        
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // When
        PaymentResponse response = paymentService.processPayment(testRequest);

        // Then
        assertNotNull(response);
        assertEquals(new BigDecimal("10.00"), response.getAmount());
    }

    @Test
    void testProcessPayment_AmountJustBelowTen_ThrowsException() throws Exception {
        // Given
        testRequest.setAmount(new BigDecimal("9.99"));
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

        // When/Then
        assertThrows(RuntimeException.class, () -> paymentService.processPayment(testRequest));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void testGetPayment_NotFound_ReturnsNull() {
        // Given
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        PaymentResponse response = paymentService.getPayment(999L);

        // Then
        assertNull(response);
        verify(paymentRepository, times(1)).findById(999L);
    }

    @Test
    void testCancelPayment_NotFound_ReturnsFalse() {
        // Given
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        // When
        boolean result = paymentService.cancelPayment(999L);

        // Then
        assertFalse(result);
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void testProcessPayment_DatabaseError_WithRetry_ThrowsException() throws Exception {
        // Given
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class)))
            .thenThrow(new RuntimeException("Database error"));
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

        // When/Then
        RuntimeException exception = assertThrows(RuntimeException.class, 
            () -> paymentService.processPayment(testRequest));
        
        assertTrue(exception.getMessage().contains("attempts"));
        verify(paymentRepository, times(3)).save(any(Payment.class)); // 3 retry attempts
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString()); // PaymentFailed event
    }

    @Test
    void testProcessPayment_KafkaPublishAllRetriesFail_ContinuesSuccessfully() throws Exception {
        // Given
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka error")));

        // When - Should not throw exception even if Kafka publish fails
        PaymentResponse response = paymentService.processPayment(testRequest);

        // Then
        assertNotNull(response);
        verify(kafkaTemplate, times(3)).send(anyString(), anyString(), anyString()); // 3 retry attempts
    }

    @Test
    void testCancelPayment_KafkaPublishFailure_StillCancels() throws Exception {
        // Given
        when(paymentRepository.findById(100L)).thenReturn(Optional.of(testPayment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);
        when(objectMapper.writeValueAsString(anyMap()))
            .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("JSON error") {});

        // When
        boolean result = paymentService.cancelPayment(100L);

        // Then
        assertTrue(result);
        verify(paymentRepository, times(1)).save(argThat(payment -> 
            payment.getStatus().equals("CANCELLED")
        ));
    }

    @Test
    void testProcessPayment_NullOrderId_HandlesGracefully() {
        // Given
        PaymentRequest nullOrderRequest = new PaymentRequest();
        nullOrderRequest.setOrderId(null);
        nullOrderRequest.setAmount(new BigDecimal("100.00"));
        
        when(paymentRepository.findByOrderId(null)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(Exception.class, () -> paymentService.processPayment(nullOrderRequest));
    }

    @Test
    void testProcessPayment_NullAmount_HandlesGracefully() {
        // Given
        PaymentRequest nullAmountRequest = new PaymentRequest();
        nullAmountRequest.setOrderId(1L);
        nullAmountRequest.setAmount(null);
        
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(Exception.class, () -> paymentService.processPayment(nullAmountRequest));
    }

    @Test
    void testProcessPayment_MultipleConsecutiveCalls_HandledCorrectly() throws Exception {
        // Given
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenReturn(testPayment);
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));

        // When
        PaymentResponse response1 = paymentService.processPayment(testRequest);
        
        // Second call should return existing payment
        when(paymentRepository.findByOrderId(1L)).thenReturn(Optional.of(testPayment));
        PaymentResponse response2 = paymentService.processPayment(testRequest);

        // Then
        assertNotNull(response1);
        assertNotNull(response2);
        assertEquals(response1.getId(), response2.getId());
        verify(paymentRepository, times(1)).save(any(Payment.class)); // Only saved once
    }
}
