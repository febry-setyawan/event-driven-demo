package com.example.payment.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {

    private Payment payment;

    @BeforeEach
    void setUp() {
        payment = new Payment();
    }

    // ===================== POSITIVE SCENARIOS =====================

    @Test
    void testDefaultConstructor_InitializesTimestamps() {
        // When
        Payment newPayment = new Payment();

        // Then
        assertNotNull(newPayment.getCreatedAt());
        assertNotNull(newPayment.getProcessedAt());
        assertTrue(newPayment.getCreatedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
        assertTrue(newPayment.getProcessedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void testParameterizedConstructor_SetsAllFields() {
        // When
        Payment newPayment = new Payment(1L, new BigDecimal("100.00"), "COMPLETED");

        // Then
        assertEquals(1L, newPayment.getOrderId());
        assertEquals(new BigDecimal("100.00"), newPayment.getAmount());
        assertEquals("COMPLETED", newPayment.getStatus());
        assertNotNull(newPayment.getCreatedAt());
        assertNotNull(newPayment.getProcessedAt());
    }

    @Test
    void testGettersAndSetters_WorkCorrectly() {
        // When
        payment.setId(100L);
        payment.setOrderId(1L);
        payment.setAmount(new BigDecimal("250.50"));
        payment.setStatus("PENDING");
        
        LocalDateTime now = LocalDateTime.now();
        payment.setCreatedAt(now);
        payment.setProcessedAt(now);

        // Then
        assertEquals(100L, payment.getId());
        assertEquals(1L, payment.getOrderId());
        assertEquals(new BigDecimal("250.50"), payment.getAmount());
        assertEquals("PENDING", payment.getStatus());
        assertEquals(now, payment.getCreatedAt());
        assertEquals(now, payment.getProcessedAt());
    }

    @Test
    void testMultipleStatusChanges() {
        // Given
        String[] statuses = {"PENDING", "PROCESSING", "COMPLETED", "CANCELLED"};

        for (String status : statuses) {
            // When
            payment.setStatus(status);
            
            // Then
            assertEquals(status, payment.getStatus());
        }
    }

    @Test
    void testDifferentAmounts() {
        // Test various amounts
        BigDecimal[] amounts = {
            BigDecimal.ZERO,
            new BigDecimal("10.00"),
            new BigDecimal("100.50"),
            new BigDecimal("999.99"),
            new BigDecimal("9999.99")
        };

        for (BigDecimal amount : amounts) {
            // When
            payment.setAmount(amount);
            
            // Then
            assertEquals(amount, payment.getAmount());
        }
    }

    // ===================== NEGATIVE SCENARIOS =====================

    @Test
    void testSetOrderId_Null_AllowsNull() {
        // When
        payment.setOrderId(null);

        // Then
        assertNull(payment.getOrderId());
    }

    @Test
    void testSetAmount_Null_AllowsNull() {
        // When
        payment.setAmount(null);

        // Then
        assertNull(payment.getAmount());
    }

    @Test
    void testSetAmount_Negative_AllowsNegative() {
        // When
        payment.setAmount(new BigDecimal("-50.00"));

        // Then
        assertEquals(new BigDecimal("-50.00"), payment.getAmount());
    }

    @Test
    void testSetStatus_Null_AllowsNull() {
        // When
        payment.setStatus(null);

        // Then
        assertNull(payment.getStatus());
    }

    @Test
    void testSetStatus_EmptyString_AllowsEmpty() {
        // When
        payment.setStatus("");

        // Then
        assertEquals("", payment.getStatus());
    }

    @Test
    void testSetCreatedAt_Null_AllowsNull() {
        // When
        payment.setCreatedAt(null);

        // Then
        assertNull(payment.getCreatedAt());
    }

    @Test
    void testSetProcessedAt_Null_AllowsNull() {
        // When
        payment.setProcessedAt(null);

        // Then
        assertNull(payment.getProcessedAt());
    }

    @Test
    void testConstructor_WithNullValues_CreatesPayment() {
        // When
        Payment nullPayment = new Payment(null, null, null);

        // Then
        assertNull(nullPayment.getOrderId());
        assertNull(nullPayment.getAmount());
        assertNull(nullPayment.getStatus());
        assertNotNull(nullPayment.getCreatedAt());
        assertNotNull(nullPayment.getProcessedAt());
    }

    @Test
    void testSetProcessedAt_FutureDate_AllowsFuture() {
        // When
        LocalDateTime futureDate = LocalDateTime.now().plusDays(1);
        payment.setProcessedAt(futureDate);

        // Then
        assertEquals(futureDate, payment.getProcessedAt());
    }

    @Test
    void testSetCreatedAt_PastDate_AllowsPast() {
        // When
        LocalDateTime pastDate = LocalDateTime.now().minusDays(1);
        payment.setCreatedAt(pastDate);

        // Then
        assertEquals(pastDate, payment.getCreatedAt());
    }

    @Test
    void testPayment_ProcessedBeforeCreated_Allowed() {
        // When
        LocalDateTime processedTime = LocalDateTime.now();
        LocalDateTime createdTime = processedTime.plusMinutes(1);
        
        payment.setProcessedAt(processedTime);
        payment.setCreatedAt(createdTime);

        // Then - No validation, so this is allowed
        assertTrue(payment.getProcessedAt().isBefore(payment.getCreatedAt()));
    }
}
