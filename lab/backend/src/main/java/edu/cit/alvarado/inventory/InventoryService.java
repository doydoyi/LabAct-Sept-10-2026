package edu.cit.alvarado.inventory;

/**
 * Public contract of the Inventory module. This is the ONLY inventory type
 * that the Order module is allowed to depend on (constructor injection of
 * this interface). The concrete implementation is package-private, so it is
 * physically impossible to compile a dependency on it from another package.
 */
public interface InventoryService {

    /**
     * Reads current inventory info for a product.
     * @throws IllegalArgumentException if the product does not exist.
     */
    InventoryItem getItem(String productId);

    /**
     * Attempts to reserve (deduct) the requested quantity from stock.
     * Rejects when the requested quantity exceeds available stock.
     */
    ReservationResult reserve(String productId, int quantity);
}
