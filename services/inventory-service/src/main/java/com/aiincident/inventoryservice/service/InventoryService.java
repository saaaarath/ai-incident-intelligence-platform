package com.aiincident.inventoryservice.service;

import com.aiincident.inventoryservice.dto.InventoryResponse;
import com.aiincident.inventoryservice.dto.ReserveInventoryRequest;
import com.aiincident.inventoryservice.entity.Inventory;
import com.aiincident.inventoryservice.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryService(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Transactional(readOnly = true)
    public InventoryResponse getInventory(String productId) {
        return inventoryRepository.findById(productId)
                .map(InventoryResponse::from)
                .orElseThrow(() -> new InventoryNotFoundException(productId));
    }

    public InventoryResponse reserve(String productId, ReserveInventoryRequest request) {
        Inventory inventory = inventoryRepository.findById(productId)
                .orElseThrow(() -> new InventoryNotFoundException(productId));
        if (inventory.getAvailableQuantity() < request.quantity()) {
            throw new InsufficientInventoryException(productId, request.quantity(), inventory.getAvailableQuantity());
        }
        inventory.reserve(request.quantity());
        return InventoryResponse.from(inventory);
    }
}
