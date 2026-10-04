package edu.cit.alvarado.inventory.event;

/**
 * Published by InventoryServiceImpl after EVERY change to a product's stock
 * (reserve for an order, restock after a cancellation, restock after a
 * supplier delivery). Listeners that only care about committed state should
 * use @TransactionalEventListener(AFTER_COMMIT). Inventory has no idea who
 * listens - it just announces the new number.
 */
public record StockChangedEvent(String productId, int newStock) {
}
