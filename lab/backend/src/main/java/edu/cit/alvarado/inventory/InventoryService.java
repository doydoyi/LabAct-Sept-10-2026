package edu.cit.alvarado.inventory;

import java.util.List;

/**
 * Public contract of the Inventory module. This is the ONLY inventory type
 * that other modules are allowed to depend on (constructor injection of
 * this interface). The concrete implementation is package-private, so it is
 * physically impossible to compile a dependency on it from another package.
 */
public interface InventoryService {

    /**
     * Reads current inventory info for a product.
     * @throws IllegalArgumentException if the product does not exist.
     */
    InventoryItem getItem(String productId);

    /** Returns the full current inventory, used by GET /api/inventory. */
    List<InventoryItem> listAll();

    /**
     * Attempts to reserve (deduct) the requested quantity from stock.
     * Rejects when the requested quantity exceeds available stock. On a
     * successful reservation that leaves stock below the configured
     * low-stock threshold, publishes a LowStockEvent.
     */
    ReservationResult reserve(String productId, int quantity);

    /**
     * Returns a previously reserved quantity to stock (used when an order
     * is cancelled).
     * @throws IllegalArgumentException if the product does not exist.
     */
    void restock(String productId, int quantity);
}
