package edu.cit.alvarado.channel;

import edu.cit.alvarado.inventory.InventoryService;
import edu.cit.alvarado.shop.LineItem;
import edu.cit.alvarado.shop.Order;
import edu.cit.alvarado.shop.OrderService;
import edu.cit.alvarado.shop.OrderStatus;
import edu.cit.alvarado.supplier.SupplierGateway;
import edu.cit.alvarado.supplier.SupplierOrderResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Tasks 4, 5 and 6. Turns Tiangge feed events into calls on our OWN Order
 * module (exactly the same OrderService the React UI uses) and reports the
 * outcome back to Tiangge.
 *
 * Exactly-once, even across crashes and redeliveries:
 *   - an eventId already in tiangge_processed_events is skipped;
 *   - a Tiangge orderId already in tiangge_orders is never ordered twice;
 *   - creating our order, remembering the eventId, linking the two orders
 *     and moving the feed cursor all happen in ONE database transaction,
 *     so after a crash either all of it happened or none of it did.
 * Reporting to Tiangge happens after that commit. If Tiangge is down, the
 * link row keeps "decision_reported = false" and retryOutstanding() sends
 * the same decision again on the next tick (Tiangge treats repeats as safe).
 *
 * Everything here runs on ONE thread (FeedPoller), so two Tiangge events
 * are never processed at the same time.
 */
@Component
class OrderFeedProcessor {

    private static final Logger log = LoggerFactory.getLogger(OrderFeedProcessor.class);

    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final TianggeClient client;
    private final TianggeOrderTranslator translator;
    private final ChannelGate gate;
    private final TransactionTemplate tx;
    private final TianggeOrderLinkRepository links;
    private final ProcessedFeedEventRepository processedEvents;
    private final FeedCursorRepository cursors;
    private final TianggeProperties properties;

    OrderFeedProcessor(OrderService orderService, InventoryService inventoryService,
                       SupplierGateway supplierGateway, TianggeClient client,
                       TianggeOrderTranslator translator, ChannelGate gate, TransactionTemplate tx,
                       TianggeOrderLinkRepository links, ProcessedFeedEventRepository processedEvents,
                       FeedCursorRepository cursors, TianggeProperties properties) {
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
        this.client = client;
        this.translator = translator;
        this.gate = gate;
        this.tx = tx;
        this.links = links;
        this.processedEvents = processedEvents;
        this.cursors = cursors;
        this.properties = properties;
    }

    // =====================================================================
    // Feed cursor
    // =====================================================================

    long currentCursor() {
        return cursors.findById(FeedCursor.SINGLETON_ID).map(FeedCursor::getLastSeq).orElse(0L);
    }

    void advanceCursor(long seq) {
        tx.executeWithoutResult(s -> moveCursor(seq));
    }

    /** Must be called inside a transaction. */
    private void moveCursor(long seq) {
        FeedCursor cursor = cursors.findById(FeedCursor.SINGLETON_ID).orElseGet(() -> new FeedCursor(0));
        cursor.advanceTo(seq);
        cursors.save(cursor);
    }

    // =====================================================================
    // One feed event
    // =====================================================================

    void handle(TianggeJson.FeedEvent event) {
        if (processedEvents.existsById(event.eventId())) {
            log.info("Feed seq={} eventId={} ({} {}) already processed - redelivery skipped",
                    event.seq(), event.eventId(), event.type(), event.orderId());
            advanceCursor(event.seq());
            return;
        }
        switch (event.type()) {
            case TianggeJson.ORDER_PLACED -> handleOrderPlaced(event);
            case TianggeJson.ORDER_CANCELLED -> handleOrderCancelled(event);
            default -> {
                log.warn("Feed seq={} has unknown type {} - recorded and skipped", event.seq(), event.type());
                markProcessedOnly(event);
            }
        }
    }

    private void handleOrderPlaced(TianggeJson.FeedEvent event) {
        String tgOrderId = event.orderId();
        if (links.existsById(tgOrderId)) {
            // Same Tiangge order delivered under a different eventId: still only one order for us.
            log.info("Tiangge order {} already has a shop order - duplicate ORDER_PLACED (seq={}) ignored",
                    tgOrderId, event.seq());
            markProcessedOnly(event);
            return;
        }

        Optional<List<LineItem>> items = translator.toLineItems(event);
        OffsetDateTime deadline = TianggeOrderTranslator.parseTime(event.decisionDeadline());

        if (items.isEmpty()) {
            // Not something our Order module can even represent (unknown product).
            TianggeOrderLink link = new TianggeOrderLink(tgOrderId, null, TianggeOrderTranslator.REJECTED,
                    "Unknown product or invalid quantity", deadline);
            gate.lock();
            try {
                tx.executeWithoutResult(s -> {
                    links.save(link);
                    recordProcessed(event);
                });
                reportDecision(link);
            } finally {
                gate.unlock();
            }
            return;
        }

        // Task 6: if we're short, make sure a restock is actually on its way
        // BEFORE deciding (outside the DB transaction - it's a network call).
        ensureRestockOrdered(items.get());

        gate.lock();
        try {
            TianggeOrderLink link = tx.execute(s -> {
                Order order = orderService.placeOrderOrBackorder(items.get(), this::everyShortItemHasOpenPurchaseOrder);
                TianggeOrderLink l = new TianggeOrderLink(tgOrderId, order.getOrderId(),
                        translator.toDecision(order), TianggeOrderTranslator.truncate(order.getReason(), 200), deadline);
                links.save(l);
                recordProcessed(event);
                return l;
            });
            log.info("Tiangge order {} (seq={}) -> shop order {} -> {}",
                    tgOrderId, event.seq(), link.shopOrderRef(), link.getDecision());
            reportDecision(link);
        } finally {
            gate.unlock();
        }
    }

    private void handleOrderCancelled(TianggeJson.FeedEvent event) {
        String tgOrderId = event.orderId();
        gate.lock();
        try {
            TianggeOrderLink link = tx.execute(s -> {
                TianggeOrderLink l = links.findById(tgOrderId).orElse(null);
                if (l == null) {
                    log.warn("Cancellation for unknown Tiangge order {} - confirming anyway", tgOrderId);
                    l = TianggeOrderLink.unknownOrderCancelled(tgOrderId);
                } else {
                    cancelShopOrder(l); // Lab 2 cancellation logic: restocks a CONFIRMED order
                    l.markCancelRequested();
                }
                links.save(l);
                recordProcessed(event);
                return l;
            });
            log.info("Tiangge order {} cancelled by customer (seq={}) -> shop order {} cancelled and restocked",
                    tgOrderId, event.seq(), link.shopOrderRef());
            confirmCancellation(link);
        } finally {
            gate.unlock();
        }
    }

    private void cancelShopOrder(TianggeOrderLink link) {
        if (link.getShopOrderId() == null) {
            return;
        }
        // Check first instead of catching OrderAlreadyCancelledException: an
        // exception escaping the @Transactional cancelOrder() would mark the
        // surrounding transaction rollback-only.
        boolean stillOpen = orderService.findOrder(link.getShopOrderId())
                .map(o -> o.getStatus() != OrderStatus.CANCELLED)
                .orElse(false);
        if (stillOpen) {
            orderService.cancelOrder(link.getShopOrderId());
        }
    }

    private void markProcessedOnly(TianggeJson.FeedEvent event) {
        tx.executeWithoutResult(s -> recordProcessed(event));
    }

    /** Must be called inside a transaction. */
    private void recordProcessed(TianggeJson.FeedEvent event) {
        processedEvents.save(new ProcessedFeedEvent(event.eventId(), event.seq(), event.type(), event.orderId()));
        moveCursor(event.seq());
    }

    // =====================================================================
    // Task 6: backorders
    // =====================================================================

    private void ensureRestockOrdered(List<LineItem> items) {
        for (LineItem shortItem : orderService.shortItems(items)) {
            String productId = shortItem.productId();
            if (supplierGateway.hasOpenPurchaseOrder(productId)) {
                continue;
            }
            try {
                // Report the stock as it will be once this order is filled
                // (it can go negative), so the reorder covers the order too,
                // not just the usual top-up to the target level.
                int stockAfterOrder = inventoryService.getItem(productId).getStock() - shortItem.quantity();
                SupplierOrderResult result = supplierGateway.reorder(productId, stockAfterOrder);
                log.info("Short on {} for a Tiangge order - restock requested: {} {}",
                        productId, result.buyerRef(), result.status());
            } catch (Exception e) {
                log.warn("Could not request restock for {}", productId, e);
            }
        }
    }

    private boolean everyShortItemHasOpenPurchaseOrder(List<LineItem> shortItems) {
        return shortItems.stream().allMatch(li -> supplierGateway.hasOpenPurchaseOrder(li.productId()));
    }

    /**
     * Tries to fill every waiting backorder. Called on every feed tick, so a
     * backorder is filled within seconds of the supplier delivery landing
     * in Inventory. A backorder is cancelled when it still can't be filled
     * and no restock is coming any more (the delivery was too small, or the
     * purchase order failed), or when it has waited too long.
     */
    void resolveBackorders() {
        for (TianggeOrderLink link : links.findByDecisionAndResolutionIsNullAndCancelRequestedFalse(
                TianggeOrderTranslator.BACKORDERED)) {
            if (!link.isDecisionReported() || link.getShopOrderId() == null) {
                continue; // Tiangge must know it's backordered before we can resolve it
            }
            gate.lock();
            try {
                resolveOne(link);
            } catch (Exception e) {
                log.warn("Could not resolve backorder {} this time", link.getTianggeOrderId(), e);
            } finally {
                gate.unlock();
            }
        }
    }

    private void resolveOne(TianggeOrderLink link) {
        Order order = orderService.fulfillBackorder(link.getShopOrderId());

        String resolution = null;
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            resolution = TianggeOrderTranslator.ACCEPTED;
        } else if (order.getStatus() == OrderStatus.CANCELLED) {
            resolution = TianggeOrderTranslator.CANCELLED;
        } else if (order.getStatus() == OrderStatus.BACKORDERED) {
            List<LineItem> lines = order.getItems().stream()
                    .map(oi -> new LineItem(oi.getProductId(), oi.getQuantity())).toList();
            boolean restockComing = orderService.shortItems(lines).stream()
                    .anyMatch(li -> supplierGateway.hasReorderInFlight(li.productId()));
            boolean waitedTooLong = link.getCreatedAt()
                    .plusSeconds(properties.getBackorderMaxWaitSeconds()).isBefore(OffsetDateTime.now());
            if (!restockComing || waitedTooLong) {
                cancelShopOrder(link);
                resolution = TianggeOrderTranslator.CANCELLED;
            }
        }

        if (resolution != null) {
            link.resolve(resolution);
            links.save(link);
            log.info("Backorder {} (shop order {}) resolved -> {}",
                    link.getTianggeOrderId(), link.shopOrderRef(), resolution);
            reportResolution(link);
        }
    }

    // =====================================================================
    // Reporting to Tiangge (+ retrying anything that didn't get through)
    // =====================================================================

    void retryOutstanding() {
        for (TianggeOrderLink link : links.findByDecisionReportedFalse()) {
            reportDecision(link);
        }
        for (TianggeOrderLink link : links.findByResolutionIsNotNullAndResolutionReportedFalse()) {
            reportResolution(link);
        }
        for (TianggeOrderLink link : links.findByCancelRequestedTrueAndCancelConfirmedFalse()) {
            confirmCancellation(link);
        }
    }

    private void reportDecision(TianggeOrderLink link) {
        TianggeClient.Result result = client.postDecision(link.getTianggeOrderId(),
                new TianggeJson.Decision(link.getDecision(), link.shopOrderRef(), link.getDecisionReason()));
        if (result.ok() || "decision_conflict".equals(result.errorCode()) || "order_not_found".equals(result.errorCode())) {
            if (!result.ok()) {
                log.error("Decision {} for {} refused permanently: {}", link.getDecision(),
                        link.getTianggeOrderId(), result.describe());
            }
            link.markDecisionReported();
            links.save(link);
        } else {
            log.warn("Decision {} for {} not delivered yet ({}) - will retry",
                    link.getDecision(), link.getTianggeOrderId(), result.describe());
        }
    }

    private void reportResolution(TianggeOrderLink link) {
        TianggeClient.Result result = client.postResolution(link.getTianggeOrderId(),
                new TianggeJson.Resolution(link.getResolution()));
        if (result.ok() || "not_backordered".equals(result.errorCode()) || "order_not_found".equals(result.errorCode())) {
            if (!result.ok()) {
                log.error("Resolution {} for {} refused: {}", link.getResolution(),
                        link.getTianggeOrderId(), result.describe());
            }
            link.markResolutionReported();
            links.save(link);
        } else {
            log.warn("Resolution for {} not delivered yet ({}) - will retry",
                    link.getTianggeOrderId(), result.describe());
        }
    }

    private void confirmCancellation(TianggeOrderLink link) {
        TianggeClient.Result result = client.postCancellation(link.getTianggeOrderId(),
                new TianggeJson.CancellationConfirmation(true));
        if (result.ok() || "not_cancelled".equals(result.errorCode()) || "order_not_found".equals(result.errorCode())) {
            if (!result.ok()) {
                log.error("Cancellation confirmation for {} refused: {}", link.getTianggeOrderId(), result.describe());
            }
            link.markCancelConfirmed();
            links.save(link);
        } else {
            log.warn("Cancellation confirmation for {} not delivered yet ({}) - will retry",
                    link.getTianggeOrderId(), result.describe());
        }
    }
}
