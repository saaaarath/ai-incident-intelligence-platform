package com.aiincident.inventoryservice.repository;

import com.aiincident.inventoryservice.entity.Inventory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface InventoryRepository extends JpaRepository<Inventory, String> {

    @Override
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Inventory> findById(String productId);
}
