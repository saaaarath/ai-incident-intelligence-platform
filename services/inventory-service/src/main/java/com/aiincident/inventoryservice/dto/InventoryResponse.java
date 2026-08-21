package com.aiincident.inventoryservice.dto;

import com.aiincident.inventoryservice.entity.Inventory;
import java.time.Instant;

public record InventoryResponse(
        String productId,
        int availableQuantity,
        Instant updatedAt
) {
    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(inventory.getProductId(), inventory.getAvailableQuantity(), inventory.getUpdatedAt());
    }
}
