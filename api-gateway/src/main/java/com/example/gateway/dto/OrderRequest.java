package com.example.gateway.dto;

import java.math.BigDecimal;

public class OrderRequest {
    private String customerId;
    private String productId;
    private Integer quantity;
    private BigDecimal amount;

    public OrderRequest() {}

    public OrderRequest(String customerId, String productId, Integer quantity, BigDecimal amount) {
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
