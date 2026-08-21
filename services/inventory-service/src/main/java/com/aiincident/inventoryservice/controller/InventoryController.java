package com.aiincident.inventoryservice.controller;

import com.aiincident.inventoryservice.dto.InventoryResponse;
import com.aiincident.inventoryservice.dto.ReserveInventoryRequest;
import com.aiincident.inventoryservice.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productId}")
    public InventoryResponse getInventory(@PathVariable String productId) {
        return inventoryService.getInventory(productId);
    }

    @PostMapping("/{productId}/reserve")
    public InventoryResponse reserve(@PathVariable String productId,
                                     @Valid @RequestBody ReserveInventoryRequest request) {
        return inventoryService.reserve(productId, request);
    }
}
