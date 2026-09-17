package edu.cit.alvarado.inventory;

import edu.cit.alvarado.inventory.event.LowStockEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    private final ApplicationEventPublisher eventPublisher;
    private final int lowStockThreshold;

    InventoryServiceImpl(InventoryRepository inventoryRepository,
                          ApplicationEventPublisher eventPublisher,
                          @Value("${app.inventory.low-stock-threshold:5}") int lowStockThreshold) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
        this.lowStockThreshold = lowStockThreshold;
    }

    @Override
    public InventoryItem getItem(String productId) {
        return inventoryRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + productId));
    }

    @Override
    public List<InventoryItem> listAll() {
        return inventoryRepository.findAll();
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

        // Business rule: after ANY successful reserve, warn if stock is now low.
        // This fires regardless of caller (single- or multi-item order), because
        // it lives right next to the state change that causes it.
        if (saved.getStock() < lowStockThreshold) {
            eventPublisher.publishEvent(new LowStockEvent(saved.getProductId(), saved.getStock(), lowStockThreshold));
        }

        return ReservationResult.approved(saved);
    }

    @Override
    @Transactional
    public void restock(String productId, int quantity) {
        InventoryItem item = inventoryRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown product: " + productId));
        item.setStock(item.getStock() + quantity);
        inventoryRepository.save(item);
    }
}
