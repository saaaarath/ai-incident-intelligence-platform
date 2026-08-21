package com.aiincident.orderservice.client;

public interface InventoryClient {

    boolean reserve(String productId, int quantity);
}