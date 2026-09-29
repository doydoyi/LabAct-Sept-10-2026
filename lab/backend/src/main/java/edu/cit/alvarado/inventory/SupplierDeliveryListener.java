package edu.cit.alvarado.inventory;

import edu.cit.alvarado.supplier.event.StockReplenishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Inventory's only coupling to the Supplier module is this one event type -
 * mirrors how Notification depends only on LowStockEvent, never on
 * InventoryService directly. Inventory never imports anything from
 * edu.cit.alvarado.supplier except this event record.
 */
@Component
class SupplierDeliveryListener {

    private static final Logger log = LoggerFactory.getLogger(SupplierDeliveryListener.class);

    private final InventoryService inventoryService;

    SupplierDeliveryListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @EventListener
    void onStockReplenished(StockReplenishedEvent event) {
        try {
            inventoryService.restock(event.productId(), event.unitsDelivered());
            log.info("Restocked {} with {} units after supplier delivery",
                    event.productId(), event.unitsDelivered());
        } catch (Exception e) {
            log.error("Failed to restock {} after supplier delivery", event.productId(), e);
        }
    }
}
