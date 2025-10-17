package com.example.order.dto;

import java.math.BigDecimal;

public class PaymentRequest {
    private Long orderId;
    private BigDecimal amount;

    public PaymentRequest() {}

    public PaymentRequest(Long orderId, BigDecimal amount) {
        this.orderId = orderId;
        this.amount = amount;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
