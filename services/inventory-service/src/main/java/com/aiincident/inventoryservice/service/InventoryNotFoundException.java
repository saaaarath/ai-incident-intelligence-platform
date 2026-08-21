package com.aiincident.inventoryservice.service;

public class InventoryNotFoundException extends RuntimeException {

    public InventoryNotFoundException(String productId) {
        super("Inventory not found: " + productId);
    }
}
