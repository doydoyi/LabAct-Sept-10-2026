package edu.cit.alvarado.channel;

import edu.cit.alvarado.inventory.InventoryService;
import edu.cit.alvarado.inventory.event.StockChangedEvent;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Task 3: keeps Tiangge's stock figures in sync, driven ONLY by Inventory's
 * StockChangedEvent (Lab 2 domain events) - there is no timer that
 * republishes stock. Whatever changed the stock (a Tiangge order, a React UI
 * order, a cancellation, a supplier delivery) ends in a StockChangedEvent,
 * so all of them reach Tiangge the same way.
 *
 * AFTER_COMMIT: we only publish numbers that are actually committed - a
 * rolled-back reservation never reaches Tiangge. Changed products are
 * collected in a "dirty" set and sent from a background thread, so a burst
 * of changes becomes one PUT, and a slow/failing Tiangge never slows down
 * the order that changed the stock. A failed PUT keeps the products dirty
 * and is retried with backoff (still only because a change is outstanding).
 */
@Component
class StockPublisher {

    private static final Logger log = LoggerFactory.getLogger(StockPublisher.class);
    private static final long MAX_RETRY_DELAY_MS = 15_000;

    private final TianggeClient client;
    private final InventoryService inventoryService;
    private final ListingCatalog catalog;
    private final ChannelGate gate;

    private final Set<String> dirty = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "tiangge-stock"));
    private volatile boolean enabled = false;
    private volatile long retryDelayMs = 1_000;

    StockPublisher(TianggeClient client, InventoryService inventoryService, ListingCatalog catalog, ChannelGate gate) {
        this.client = client;
        this.inventoryService = inventoryService;
        this.catalog = catalog;
        this.gate = gate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    void onStockChanged(StockChangedEvent event) {
        dirty.add(event.productId());
        scheduleFlush(0);
    }

    /** Called once at startup, right after listings are published: every listed product is "changed". */
    boolean publishAllNow() {
        catalog.listings().forEach(l -> dirty.add(ListingCatalog.toProductId(l.sellerSku())));
        boolean ok = flush();
        enabled = true;
        if (!dirty.isEmpty()) {
            scheduleFlush(retryDelayMs);
        }
        return ok;
    }

    private void scheduleFlush(long delayMs) {
        if (!enabled) {
            return; // startup's publishAllNow() will send everything once listings exist
        }
        if (flushScheduled.compareAndSet(false, true)) {
            executor.schedule(this::flushFromExecutor, delayMs, TimeUnit.MILLISECONDS);
        }
    }

    private void flushFromExecutor() {
        flushScheduled.set(false);
        boolean ok = flush();
        if (!ok) {
            long delay = retryDelayMs;
            retryDelayMs = Math.min(retryDelayMs * 2, MAX_RETRY_DELAY_MS);
            scheduleFlush(delay);
        } else {
            retryDelayMs = 1_000;
            if (!dirty.isEmpty()) {
                scheduleFlush(0); // more changes arrived while we were sending
            }
        }
    }

    private boolean flush() {
        if (dirty.isEmpty()) {
            return true;
        }
        gate.lock(); // never overtake a Tiangge decision that is still being reported
        try {
            List<String> batch = new ArrayList<>(dirty);
            dirty.removeAll(batch);

            List<TianggeJson.StockEntry> entries = new ArrayList<>();
            for (String productId : batch) {
                if (catalog.isListed(productId)) {
                    entries.add(new TianggeJson.StockEntry(ListingCatalog.toSellerSku(productId),
                            Math.max(0, inventoryService.getItem(productId).getStock())));
                }
            }
            if (entries.isEmpty()) {
                return true;
            }

            TianggeClient.Result result = client.putStock(entries);
            if (result.ok()) {
                log.info("Stock published to Tiangge: {}", entries);
                return true;
            }
            if (result.retryable()) {
                dirty.addAll(batch);
                log.warn("Stock publish failed ({}), will retry: {}", result.describe(), entries);
                return false;
            }
            log.error("Stock publish refused by Tiangge ({}): {}", result.describe(), entries);
            return true; // e.g. unknown_seller_sku - retrying the same content would not help
        } catch (Exception e) {
            log.error("Stock publish crashed, will retry", e);
            return false;
        } finally {
            gate.unlock();
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
