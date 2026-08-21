package com.aiincident.inventoryservice.service;

public class InsufficientInventoryException extends RuntimeException {

    public InsufficientInventoryException(String productId, int requested, int available) {
        super("Insufficient inventory for product " + productId + ": requested " + requested
                + ", available " + available);
    }
}
