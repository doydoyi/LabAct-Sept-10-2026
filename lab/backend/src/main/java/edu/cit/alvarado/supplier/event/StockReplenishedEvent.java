package edu.cit.alvarado.supplier.event;

/**
 * Published by SupplierOrderPollingJob the moment a supplier order's status
 * changes to DELIVERED. The Inventory module depends only on this record,
 * never on anything else in the supplier package - mirrors how Notification
 * depends only on LowStockEvent from the inventory package.
 */
public record StockReplenishedEvent(String productId, int unitsDelivered) {
}
