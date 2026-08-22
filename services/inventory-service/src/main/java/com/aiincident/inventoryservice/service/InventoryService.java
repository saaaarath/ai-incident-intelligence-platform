package com.aiincident.inventoryservice.service;

import com.aiincident.inventoryservice.dto.InventoryResponse;
import com.aiincident.inventoryservice.dto.ReserveInventoryRequest;
import com.aiincident.inventoryservice.entity.Inventory;
import com.aiincident.inventoryservice.repository.InventoryRepository;
import com.aiincident.logging.StructuredLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StructuredLogger operationalLogger;

    public InventoryService(InventoryRepository inventoryRepository, ObjectMapper objectMapper) {
        this.inventoryRepository = inventoryRepository;
        this.operationalLogger = new StructuredLogger(
                LoggerFactory.getLogger(InventoryService.class), objectMapper, "inventory-service");
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
            operationalLogger.warn(
                "INVENTORY_RESERVATION_FAILED",
                "Insufficient inventory",
                Map.of("productId", productId, "requestedQuantity", request.quantity()));
            throw new InsufficientInventoryException(productId, request.quantity(), inventory.getAvailableQuantity());
        }
        inventory.reserve(request.quantity());
        operationalLogger.info(
            "INVENTORY_RESERVED",
            "Inventory reserved",
            Map.of("productId", productId, "quantity", request.quantity()));
        return InventoryResponse.from(inventory);
    }
}
