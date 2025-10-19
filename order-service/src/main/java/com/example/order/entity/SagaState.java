package com.example.order.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "saga_state")
public class SagaState {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;
    
    @Column(nullable = false, length = 50)
    private String status;
    
    @Column(name = "current_step", length = 50)
    private String currentStep;
    
    @Column(name = "payment_id")
    private Long paymentId;
    
    @Column(name = "timeout_at")
    private LocalDateTime timeoutAt;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public SagaState() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public SagaState(Long orderId, String status, String currentStep) {
        this();
        this.orderId = orderId;
        this.status = status;
        this.currentStep = currentStep;
        this.timeoutAt = LocalDateTime.now().plusSeconds(60);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { 
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public String getCurrentStep() { return currentStep; }
    public void setCurrentStep(String currentStep) { 
        this.currentStep = currentStep;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getPaymentId() { return paymentId; }
    public void setPaymentId(Long paymentId) { this.paymentId = paymentId; }

    public LocalDateTime getTimeoutAt() { return timeoutAt; }
    public void setTimeoutAt(LocalDateTime timeoutAt) { this.timeoutAt = timeoutAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
