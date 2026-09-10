package edu.cit.alvarado.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Package-private implementation. Spring can still find and wire this bean
 * (component scanning does not care about visibility), but no class outside
 * edu.cit.alvarado.inventory can reference the type InventoryServiceImpl
 * directly - the compiler forbids it. Other modules are forced to depend on
 * the public InventoryService interface instead.
 */
@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    public InventoryItem getItem(String productId) {
        return inventoryRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + productId));
    }

    @Override
    @Transactional
    public ReservationResult reserve(String productId, int quantity) {
        InventoryItem item = inventoryRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + productId));

        if (quantity <= 0) {
            return ReservationResult.rejected("Quantity must be greater than zero", item);
        }

        if (item.getStock() < quantity) {
            return ReservationResult.rejected(
                    "Requested quantity (" + quantity + ") exceeds available stock (" + item.getStock() + ")",
                    item);
        }

        item.setStock(item.getStock() - quantity);
        InventoryItem saved = inventoryRepository.save(item);
        return ReservationResult.approved(saved);
    }
}
