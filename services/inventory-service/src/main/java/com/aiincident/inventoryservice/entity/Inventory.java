package com.aiincident.inventoryservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(nullable = false, updatable = false, length = 128)
    private String productId;

    @Column(nullable = false)
    private int availableQuantity;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Inventory() {
    }

    public Inventory(String productId, int availableQuantity) {
        this.productId = productId;
        this.availableQuantity = availableQuantity;
    }

    @PrePersist
    void setCreatedTimestamp() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    void setUpdatedTimestamp() {
        updatedAt = Instant.now();
    }

    public void reserve(int quantity) {
        availableQuantity -= quantity;
    }

    public String getProductId() {
        return productId;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
