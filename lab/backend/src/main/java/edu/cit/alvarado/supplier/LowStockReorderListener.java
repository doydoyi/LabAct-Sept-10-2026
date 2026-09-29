package edu.cit.alvarado.supplier;

import edu.cit.alvarado.inventory.event.LowStockEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * This is the Lab 3 upgrade to the auto-reorder rule: where Lab 2 only had
 * NotificationServiceImpl log a low-stock alert, this listener additionally
 * calls SupplierGateway to place a real purchase order.
 *
 * Two things are deliberate here, worth being able to explain:
 *
 * 1. @TransactionalEventListener(phase = AFTER_COMMIT) instead of a plain
 *    @EventListener - unlike NotificationServiceImpl (which intentionally
 *    stays synchronous, in the SAME transaction, per its own Lab 2 design
 *    comment), this listener only runs once the reserve() transaction has
 *    actually committed. A rejected/rolled-back reservation must never
 *    trigger a real purchase order.
 * 2. @Async - placing an order involves a real network call to an external
 *    system with up to a 3-second timeout and up to 3 retries (Part D).
 *    Running that synchronously would make LegacySupply's slowness or an
 *    outage directly slow down or fail the customer's original request.
 *    Async decouples them completely. (Requires @EnableAsync, added to
 *    AlvaradoApplication.)
 */
@Component
class LowStockReorderListener {

    private static final Logger log = LoggerFactory.getLogger(LowStockReorderListener.class);

    private final SupplierGateway supplierGateway;

    LowStockReorderListener(SupplierGateway supplierGateway) {
        this.supplierGateway = supplierGateway;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onLowStock(LowStockEvent event) {
        try {
            SupplierOrderResult result = supplierGateway.reorder(event.productId(), event.remainingStock());
            log.info("Auto-reorder result for {}: buyerRef={} status={}",
                    event.productId(), result.buyerRef(), result.status());
        } catch (Exception e) {
            // A supplier-side problem must never propagate back into
            // whatever triggered the original reservation - it already
            // committed successfully by the time this runs.
            log.error("Auto-reorder failed for product {}", event.productId(), e);
        }
    }
}
