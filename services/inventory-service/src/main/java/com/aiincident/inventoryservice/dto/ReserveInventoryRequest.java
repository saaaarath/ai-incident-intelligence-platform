package com.aiincident.inventoryservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReserveInventoryRequest(
        @NotNull(message = "quantity must not be null")
        @Positive(message = "quantity must be greater than zero")
        Integer quantity
) {
}
