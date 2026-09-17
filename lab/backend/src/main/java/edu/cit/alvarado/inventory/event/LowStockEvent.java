package edu.cit.alvarado.inventory.event;

/**
 * Published by InventoryServiceImpl right after any successful reserve()
 * leaves a product's stock below the configured threshold. The Notification
 * module depends only on this record, never on InventoryService.
 */
public record LowStockEvent(String productId, int remainingStock, int threshold) {
}
