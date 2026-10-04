package edu.cit.alvarado.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * A reorder is left PENDING (never discarded) whenever LegacySupply was
 * unavailable or rate-limited. This job periodically retries those rows,
 * reusing each order's already-stored requestId - so even if an earlier
 * attempt actually succeeded on LegacySupply's side but we never saw the
 * response (e.g. our own timeout), resubmitting is safe: LegacySupply
 * recognizes the repeated X-Request-Id and does not process it twice.
 */
@Component
class SupplierOrderRetryJob {

    private static final Logger log = LoggerFactory.getLogger(SupplierOrderRetryJob.class);

    private final SupplierOrderRepository repository;
    private final LegacySupplyGatewayImpl gateway;

    SupplierOrderRetryJob(SupplierOrderRepository repository, LegacySupplyGatewayImpl gateway) {
        this.repository = repository;
        this.gateway = gateway;
    }

    @Scheduled(initialDelay = 20_000, fixedDelayString = "${app.supplier.retry-interval-ms:60000}")
    void retryPendingOrders() {
        List<SupplierOrder> pending = repository.findByStatus(SupplierOrderStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }
        log.info("Retrying {} PENDING supplier order(s)", pending.size());
        for (SupplierOrder order : pending) {
            try {
                gateway.attemptSubmit(order);
            } catch (Exception e) {
                log.error("Retry attempt threw for buyerRef={}", order.getBuyerRef(), e);
            }
        }
    }
}
