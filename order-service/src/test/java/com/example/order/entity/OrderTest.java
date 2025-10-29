package com.example.order.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order();
    }

    // ===================== POSITIVE SCENARIOS =====================

    @Test
    void testDefaultConstructor_InitializesTimestamps() {
        // When
        Order newOrder = new Order();

        // Then
        assertNotNull(newOrder.getCreatedAt());
        assertNotNull(newOrder.getUpdatedAt());
        assertTrue(newOrder.getCreatedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
        assertTrue(newOrder.getUpdatedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void testParameterizedConstructor_SetsAllFields() {
        // When
        Order newOrder = new Order("customer1", "product1", 5, new BigDecimal("100.00"), "PENDING");

        // Then
        assertEquals("customer1", newOrder.getCustomerId());
        assertEquals("product1", newOrder.getProductId());
        assertEquals(5, newOrder.getQuantity());
        assertEquals(new BigDecimal("100.00"), newOrder.getAmount());
        assertEquals("PENDING", newOrder.getStatus());
        assertNotNull(newOrder.getCreatedAt());
        assertNotNull(newOrder.getUpdatedAt());
    }

    @Test
    void testSetStatus_UpdatesTimestamp() throws InterruptedException {
        // Given
        LocalDateTime originalUpdatedAt = order.getUpdatedAt();
        Thread.sleep(10); // Small delay to ensure timestamp difference

        // When
        order.setStatus("COMPLETED");

        // Then
        assertEquals("COMPLETED", order.getStatus());
        assertTrue(order.getUpdatedAt().isAfter(originalUpdatedAt));
    }

    @Test
    void testGettersAndSetters_WorkCorrectly() {
        // When
        order.setId(1L);
        order.setCustomerId("customer123");
        order.setProductId("product456");
        order.setQuantity(10);
        order.setAmount(new BigDecimal("250.50"));
        order.setStatus("PENDING");
        
        LocalDateTime now = LocalDateTime.now();
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        // Then
        assertEquals(1L, order.getId());
        assertEquals("customer123", order.getCustomerId());
        assertEquals("product456", order.getProductId());
        assertEquals(10, order.getQuantity());
        assertEquals(new BigDecimal("250.50"), order.getAmount());
        assertEquals("PENDING", order.getStatus());
        assertEquals(now, order.getCreatedAt());
        assertEquals(now, order.getUpdatedAt());
    }

    @Test
    void testMultipleStatusChanges_UpdatesTimestampEachTime() throws InterruptedException {
        // Given
        String[] statuses = {"PENDING", "PROCESSING", "COMPLETED"};
        LocalDateTime previousTime = order.getUpdatedAt();

        for (String status : statuses) {
            Thread.sleep(10);
            
            // When
            order.setStatus(status);
            
            // Then
            assertEquals(status, order.getStatus());
            assertTrue(order.getUpdatedAt().isAfter(previousTime));
            previousTime = order.getUpdatedAt();
        }
    }

    // ===================== NEGATIVE SCENARIOS =====================

    @Test
    void testSetCustomerId_Null_AllowsNull() {
        // When
        order.setCustomerId(null);

        // Then
        assertNull(order.getCustomerId());
    }

    @Test
    void testSetProductId_Null_AllowsNull() {
        // When
        order.setProductId(null);

        // Then
        assertNull(order.getProductId());
    }

    @Test
    void testSetQuantity_Zero_AllowsZero() {
        // When
        order.setQuantity(0);

        // Then
        assertEquals(0, order.getQuantity());
    }

    @Test
    void testSetQuantity_Negative_AllowsNegative() {
        // When
        order.setQuantity(-5);

        // Then
        assertEquals(-5, order.getQuantity());
    }

    @Test
    void testSetAmount_Null_AllowsNull() {
        // When
        order.setAmount(null);

        // Then
        assertNull(order.getAmount());
    }

    @Test
    void testSetAmount_Zero_AllowsZero() {
        // When
        order.setAmount(BigDecimal.ZERO);

        // Then
        assertEquals(BigDecimal.ZERO, order.getAmount());
    }

    @Test
    void testSetAmount_Negative_AllowsNegative() {
        // When
        order.setAmount(new BigDecimal("-50.00"));

        // Then
        assertEquals(new BigDecimal("-50.00"), order.getAmount());
    }

    @Test
    void testSetStatus_Null_AllowsNull() {
        // When
        order.setStatus(null);

        // Then
        assertNull(order.getStatus());
    }

    @Test
    void testSetStatus_EmptyString_AllowsEmpty() {
        // When
        order.setStatus("");

        // Then
        assertEquals("", order.getStatus());
    }

    @Test
    void testConstructor_WithNullValues_CreatesOrder() {
        // When
        Order nullOrder = new Order(null, null, null, null, null);

        // Then
        assertNull(nullOrder.getCustomerId());
        assertNull(nullOrder.getProductId());
        assertNull(nullOrder.getQuantity());
        assertNull(nullOrder.getAmount());
        assertNull(nullOrder.getStatus());
        assertNotNull(nullOrder.getCreatedAt());
        assertNotNull(nullOrder.getUpdatedAt());
    }
}
