package edu.cit.alvarado.supplier;

import edu.cit.alvarado.supplier.event.StockReplenishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Checks every open (submitted-but-not-yet-delivered) order's status and
 * maps LegacySupply's numeric StatusCode onto our own enum. When an order
 * newly transitions to DELIVERED, publishes StockReplenishedEvent so
 * Inventory can restock - Order and Inventory never call this module
 * directly, they only ever react to that event.
 */
@Component
class SupplierOrderPollingJob {

    private static final Logger log = LoggerFactory.getLogger(SupplierOrderPollingJob.class);

    private static final List<SupplierOrderStatus> OPEN =
            List.of(SupplierOrderStatus.ACCEPTED, SupplierOrderStatus.PICKING, SupplierOrderStatus.SHIPPED);

    private final SupplierOrderRepository repository;
    private final LegacySupplyClient client;
    private final ApplicationEventPublisher eventPublisher;

    SupplierOrderPollingJob(SupplierOrderRepository repository, LegacySupplyClient client,
                             ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.client = client;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${app.supplier.poll-interval-ms:120000}")
    void pollOpenOrders() {
        for (SupplierOrderStatus status : OPEN) {
            for (SupplierOrder order : repository.findByStatus(status)) {
                pollOne(order);
            }
        }
    }

    private void pollOne(SupplierOrder order) {
        if (order.getPoNumber() == null) {
            return; // shouldn't happen for ACCEPTED/PICKING/SHIPPED, but be defensive
        }

        LegacySupplyClient.Outcome outcome = client.getOrderStatus(order.getPoNumber());

        if (!outcome.isSuccess()) {
            // Don't guess, don't crash - just log and leave the order in
            // its last known status; the next poll will try again.
            log.warn("Could not refresh status for {}: httpStatus={} error={}",
                    order.getBuyerRef(), outcome.httpStatus,
                    outcome.error != null ? outcome.error.code() : "transport failure");
            return;
        }

        LegacySupplyXml.PurchaseOrderAck ack = LegacySupplyXml.parsePurchaseOrderAck(outcome.body);
        SupplierOrderStatus previousStatus = order.getStatus();
        SupplierOrderStatus newStatus = LegacySupplyGatewayImpl.mapStatusCode(ack.statusCode());

        order.setLegacyStatusCode(ack.statusCode());
        order.setStatus(newStatus);
        repository.save(order);

        if (newStatus == SupplierOrderStatus.DELIVERED && previousStatus != SupplierOrderStatus.DELIVERED) {
            int unitsDelivered = order.getQty() * order.getPackSize();
            log.info("Order {} delivered: {} units of {}", order.getBuyerRef(), unitsDelivered, order.getProductId());
            eventPublisher.publishEvent(new StockReplenishedEvent(order.getProductId(), unitsDelivered));
        }
    }
}
