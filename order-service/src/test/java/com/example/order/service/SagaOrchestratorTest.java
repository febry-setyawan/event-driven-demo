package com.example.order.service;

import com.example.order.entity.SagaEvent;
import com.example.order.entity.SagaState;
import com.example.order.repository.SagaEventRepository;
import com.example.order.repository.SagaStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SagaOrchestratorTest {

    @Mock
    private SagaStateRepository sagaStateRepository;

    @Mock
    private SagaEventRepository sagaEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private SagaOrchestrator sagaOrchestrator;

    private SagaState testSagaState;

    @BeforeEach
    void setUp() {
        testSagaState = new SagaState("saga123", 1L, "WAITING", "ORDER_CREATED");
        testSagaState.setPaymentId(100L);
        ReflectionTestUtils.setField(sagaOrchestrator, "paymentServiceUrl", "http://payment-service:8082");
    }

    // ===================== POSITIVE SCENARIOS =====================

    @Test
    void testStartSaga_Success() {
        // Given
        when(sagaStateRepository.save(any(SagaState.class))).thenReturn(testSagaState);
        when(sagaEventRepository.save(any(SagaEvent.class))).thenReturn(new SagaEvent());

        // When
        String sagaId = sagaOrchestrator.startSaga(1L, "customer1", "product1", 5, new BigDecimal("100.00"));

        // Then
        assertNotNull(sagaId);
        verify(sagaStateRepository, times(1)).save(any(SagaState.class));
        verify(sagaEventRepository, atLeastOnce()).save(any(SagaEvent.class));
    }

    @Test
    void testStartSagaWithId_Success() {
        // Given
        when(sagaStateRepository.save(any(SagaState.class))).thenReturn(testSagaState);
        when(sagaEventRepository.save(any(SagaEvent.class))).thenReturn(new SagaEvent());

        // When
        String sagaId = sagaOrchestrator.startSagaWithId("saga123", 1L, "customer1", "product1", 5, new BigDecimal("100.00"));

        // Then
        assertEquals("saga123", sagaId);
        verify(sagaStateRepository, times(1)).save(argThat(saga -> 
            saga.getSagaId().equals("saga123") && 
            saga.getOrderId().equals(1L) &&
            saga.getStatus().equals("WAITING") &&
            saga.getCurrentStep().equals("ORDER_CREATED")
        ));
        verify(sagaEventRepository, atLeastOnce()).save(any(SagaEvent.class));
    }

    @Test
    void testProcessPayment_Success() {
        // Given
        when(sagaStateRepository.findByOrderId(1L)).thenReturn(Optional.of(testSagaState));
        when(sagaStateRepository.save(any(SagaState.class))).thenReturn(testSagaState);
        when(sagaEventRepository.save(any(SagaEvent.class))).thenReturn(new SagaEvent());

        // When
        sagaOrchestrator.processPayment(1L, 100L);

        // Then
        verify(sagaStateRepository, times(1)).save(argThat(saga -> 
            saga.getStatus().equals("PROCESSING") &&
            saga.getCurrentStep().equals("PAYMENT_PROCESSING") &&
            saga.getPaymentId().equals(100L)
        ));
        verify(sagaEventRepository, atLeastOnce()).save(any(SagaEvent.class));
    }

    @Test
    void testCompleteSaga_Success() {
        // Given
        when(sagaStateRepository.findByOrderId(1L)).thenReturn(Optional.of(testSagaState));
        when(sagaStateRepository.save(any(SagaState.class))).thenReturn(testSagaState);
        when(sagaEventRepository.save(any(SagaEvent.class))).thenReturn(new SagaEvent());

        // When
        sagaOrchestrator.completeSaga(1L);

        // Then
        verify(sagaStateRepository, times(1)).save(argThat(saga -> 
            saga.getStatus().equals("COMPLETED") &&
            saga.getCurrentStep().equals("PAYMENT_COMPLETED")
        ));
        verify(sagaEventRepository, atLeastOnce()).save(any(SagaEvent.class));
    }

    @Test
    void testFailSaga_Success() {
        // Given
        when(sagaStateRepository.findByOrderId(1L)).thenReturn(Optional.of(testSagaState));
        when(sagaStateRepository.save(any(SagaState.class))).thenReturn(testSagaState);
        when(sagaEventRepository.save(any(SagaEvent.class))).thenReturn(new SagaEvent());

        // When
        sagaOrchestrator.failSaga(1L);

        // Then
        verify(sagaStateRepository, times(1)).save(argThat(saga -> 
            saga.getStatus().equals("FAILED") &&
            saga.getCurrentStep().equals("PAYMENT_FAILED")
        ));
        verify(sagaEventRepository, atLeastOnce()).save(any(SagaEvent.class));
    }

    @Test
    void testRefundPayment_Success() {
        // Given
        when(sagaStateRepository.findByOrderId(1L)).thenReturn(Optional.of(testSagaState));
        when(sagaStateRepository.save(any(SagaState.class))).thenReturn(testSagaState);
        when(sagaEventRepository.save(any(SagaEvent.class))).thenReturn(new SagaEvent());

        // When
        sagaOrchestrator.refundPayment(1L);

        // Then
        verify(sagaStateRepository, times(1)).save(argThat(saga -> 
            saga.getStatus().equals("REFUNDED") &&
            saga.getCurrentStep().equals("PAYMENT_REFUNDED")
        ));
        verify(sagaEventRepository, atLeastOnce()).save(any(SagaEvent.class));
    }

    @Test
    void testCompensate_WithPaymentId_Success() throws Exception {
        // Given
        testSagaState.setPaymentId(100L);
        when(sagaStateRepository.save(any(SagaState.class))).thenReturn(testSagaState);
        when(sagaEventRepository.save(any(SagaEvent.class))).thenReturn(new SagaEvent());
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

        // When
        sagaOrchestrator.compensate(testSagaState);

        // Then
        verify(sagaStateRepository, times(2)).save(any(SagaState.class)); // Initial compensating + final failed
        verify(sagaEventRepository, atLeast(3)).save(any(SagaEvent.class));
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString()); // Only cancelOrder publishes
    }

    @Test
    void testCompensate_WithoutPaymentId_Success() throws Exception {
        // Given
        testSagaState.setPaymentId(null);
        when(sagaStateRepository.save(any(SagaState.class))).thenReturn(testSagaState);
        when(sagaEventRepository.save(any(SagaEvent.class))).thenReturn(new SagaEvent());
        when(objectMapper.writeValueAsString(anyMap())).thenReturn("{}");

        // When
        sagaOrchestrator.compensate(testSagaState);

        // Then
        verify(sagaStateRepository, times(2)).save(any(SagaState.class));
        verify(sagaEventRepository, atLeast(2)).save(any(SagaEvent.class));
        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), anyString()); // Only order cancellation
    }

    @Test
    void testGetSagaState_Found() {
        // Given
        when(sagaStateRepository.findByOrderId(1L)).thenReturn(Optional.of(testSagaState));

        // When
        Optional<SagaState> result = sagaOrchestrator.getSagaState(1L);

        // Then
        assertTrue(result.isPresent());
        assertEquals("saga123", result.get().getSagaId());
        assertEquals(1L, result.get().getOrderId());
    }

    @Test
    void testCheckTimeouts_ProcessesTimedOutSagas() {
        // Given
        SagaState timedOutSaga = new SagaState("saga456", 2L, "WAITING", "ORDER_CREATED");
        timedOutSaga.setTimeoutAt(LocalDateTime.now().minusMinutes(10));
        
        when(sagaStateRepository.findAll()).thenReturn(Arrays.asList(testSagaState, timedOutSaga));
        when(sagaStateRepository.save(any(SagaState.class))).thenReturn(timedOutSaga);
        when(sagaEventRepository.save(any(SagaEvent.class))).thenReturn(new SagaEvent());

        // When
        sagaOrchestrator.checkTimeouts();

        // Then
        verify(sagaStateRepository, atLeastOnce()).save(argThat(saga -> 
            saga.getStatus().equals("NO_PAYMENT") &&
            saga.getCurrentStep().equals("TIMEOUT")
        ));
    }

    // ===================== NEGATIVE SCENARIOS =====================

    @Test
    void testProcessPayment_OrderNotFound_ThrowsException() {
        // Given
        when(sagaStateRepository.findByOrderId(999L)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(Exception.class, () -> sagaOrchestrator.processPayment(999L, 100L));
    }

    @Test
    void testCompleteSaga_OrderNotFound_ThrowsException() {
        // Given
        when(sagaStateRepository.findByOrderId(999L)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(Exception.class, () -> sagaOrchestrator.completeSaga(999L));
    }

    @Test
    void testFailSaga_OrderNotFound_ThrowsException() {
        // Given
        when(sagaStateRepository.findByOrderId(999L)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(Exception.class, () -> sagaOrchestrator.failSaga(999L));
    }

    @Test
    void testRefundPayment_OrderNotFound_ThrowsException() {
        // Given
        when(sagaStateRepository.findByOrderId(999L)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(Exception.class, () -> sagaOrchestrator.refundPayment(999L));
    }

    @Test
    void testGetSagaState_NotFound() {
        // Given
        when(sagaStateRepository.findByOrderId(999L)).thenReturn(Optional.empty());

        // When
        Optional<SagaState> result = sagaOrchestrator.getSagaState(999L);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void testCompensate_KafkaPublishFailure_HandlesGracefully() throws Exception {
        // Given
        testSagaState.setPaymentId(100L);
        when(sagaStateRepository.save(any(SagaState.class))).thenReturn(testSagaState);
        when(sagaEventRepository.save(any(SagaEvent.class))).thenReturn(new SagaEvent());
        when(objectMapper.writeValueAsString(anyMap()))
            .thenThrow(new com.fasterxml.jackson.core.JsonProcessingException("JSON error") {});

        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> sagaOrchestrator.compensate(testSagaState));
        verify(sagaStateRepository, times(2)).save(any(SagaState.class));
    }

    @Test
    void testCheckTimeouts_NoTimedOutSagas_DoesNothing() {
        // Given
        testSagaState.setTimeoutAt(LocalDateTime.now().plusMinutes(10)); // Future timeout
        when(sagaStateRepository.findAll()).thenReturn(Arrays.asList(testSagaState));

        // When
        sagaOrchestrator.checkTimeouts();

        // Then
        verify(sagaStateRepository, never()).save(any(SagaState.class));
    }

    @Test
    void testCheckTimeouts_NonWaitingSaga_Ignored() {
        // Given
        testSagaState.setStatus("COMPLETED");
        testSagaState.setTimeoutAt(LocalDateTime.now().minusMinutes(10));
        when(sagaStateRepository.findAll()).thenReturn(Arrays.asList(testSagaState));

        // When
        sagaOrchestrator.checkTimeouts();

        // Then
        verify(sagaStateRepository, never()).save(any(SagaState.class));
    }

    @Test
    void testCheckTimeouts_NullTimeoutAt_Ignored() {
        // Given
        testSagaState.setStatus("WAITING");
        testSagaState.setTimeoutAt(null);
        when(sagaStateRepository.findAll()).thenReturn(Arrays.asList(testSagaState));

        // When
        sagaOrchestrator.checkTimeouts();

        // Then
        verify(sagaStateRepository, never()).save(any(SagaState.class));
    }

    @Test
    void testStartSaga_RepositoryException_PropagatesException() {
        // Given
        when(sagaStateRepository.save(any(SagaState.class)))
            .thenThrow(new RuntimeException("Database error"));

        // When/Then
        assertThrows(RuntimeException.class, () -> 
            sagaOrchestrator.startSaga(1L, "customer1", "product1", 5, new BigDecimal("100.00"))
        );
    }

    @Test
    void testCompensate_EventLoggingFailure_ContinuesCompensation() {
        // Given
        testSagaState.setPaymentId(null);
        when(sagaStateRepository.save(any(SagaState.class))).thenReturn(testSagaState);
        when(sagaEventRepository.save(any(SagaEvent.class)))
            .thenThrow(new RuntimeException("Event logging failed"));

        // When/Then - Should continue despite event logging failure
        assertDoesNotThrow(() -> sagaOrchestrator.compensate(testSagaState));
        verify(sagaStateRepository, times(2)).save(any(SagaState.class));
    }
}
