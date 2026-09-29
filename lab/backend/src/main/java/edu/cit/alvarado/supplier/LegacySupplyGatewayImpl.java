package edu.cit.alvarado.supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
class LegacySupplyGatewayImpl implements SupplierGateway {

    private static final Logger log = LoggerFactory.getLogger(LegacySupplyGatewayImpl.class);
    private static final List<SupplierOrderStatus> IN_FLIGHT =
            List.of(SupplierOrderStatus.PENDING, SupplierOrderStatus.ACCEPTED,
                    SupplierOrderStatus.PICKING, SupplierOrderStatus.SHIPPED);

    private final SupplierOrderRepository repository;
    private final LegacySupplyClient client;
    private final SupplierProperties properties;

    LegacySupplyGatewayImpl(SupplierOrderRepository repository, LegacySupplyClient client,
                             SupplierProperties properties) {
        this.repository = repository;
        this.client = client;
        this.properties = properties;
    }

    @Override
    public SupplierOrderResult reorder(String productId, int currentStock) {
        SupplierProperties.CatalogEntry entry = properties.getCatalog().get(productId);
        if (entry == null || entry.getSku() == null || entry.getSku().isBlank()) {
            // Configuration bug, not a supplier failure - fail loudly so it
            // gets fixed in application.properties, per INTEGRATION.md item 1.
            throw new IllegalStateException(
                    "No LegacySupply catalog mapping configured for product " + productId
                            + " - add app.supplier.catalog." + productId + ".sku/.pack-size");
        }

        // Don't pile up a second reorder while one is already open.
        List<SupplierOrder> existing = repository.findByProductIdAndStatusIn(productId, IN_FLIGHT);
        if (!existing.isEmpty()) {
            SupplierOrder open = existing.get(0);
            log.info("Reorder already in flight for {} (buyerRef={}, status={}); skipping new order",
                    productId, open.getBuyerRef(), open.getStatus());
            return new SupplierOrderResult(open.getBuyerRef(), open.getStatus());
        }

        int unitsNeeded = Math.max(properties.getReorderTargetLevel() - currentStock, entry.getPackSize());
        int qtyPacks = ceilDiv(unitsNeeded, entry.getPackSize());
        qtyPacks = Math.max(1, Math.min(99, qtyPacks)); // manual: whole number 1-99

        String requestId = UUID.randomUUID().toString();
        SupplierOrder order = new SupplierOrder(productId, requestId, entry.getSku(), qtyPacks, entry.getPackSize());
        order = repository.save(order); // generates the id we need for buyerRef
        order.setBuyerRef("RO-" + order.getId());
        order = repository.save(order);

        return attemptSubmit(order);
    }

    /**
     * Submits (or re-submits) an order that already has a row in
     * supplier_orders. Used both for brand-new reorders and by
     * SupplierOrderRetryJob for orders still stuck in PENDING - both paths
     * reuse the SAME stored requestId, so a retry can never create a
     * duplicate purchase order even if an earlier attempt actually
     * succeeded on LegacySupply's side but we never saw the response.
     */
    SupplierOrderResult attemptSubmit(SupplierOrder order) {
        LegacySupplyClient.Outcome outcome = client.placeOrder(
                order.getSupplierSku(), order.getQty(), order.getBuyerRef(), order.getRequestId());

        if (outcome.isSuccess()) {
            LegacySupplyXml.PurchaseOrderAck ack = LegacySupplyXml.parsePurchaseOrderAck(outcome.body);
            order.setPoNumber(ack.poNumber());
            order.setUom(ack.uom());
            order.setStatus(mapStatusCode(ack.statusCode()));
            order.setLegacyStatusCode(ack.statusCode());
            repository.save(order);
            log.info("Reorder submitted: product={} buyerRef={} poNumber={} status={}",
                    order.getProductId(), order.getBuyerRef(), order.getPoNumber(), order.getStatus());
            return new SupplierOrderResult(order.getBuyerRef(), order.getStatus());
        }

        if (outcome.isRateLimited() || outcome.isRetryableServerError()) {
            // Leave it PENDING - SupplierOrderRetryJob will try again later.
            // This is never lost: the row already exists in supplier_orders.
            String code = outcome.error != null ? outcome.error.code() : "TRANSPORT";
            String message = outcome.error != null ? outcome.error.message() : "No response from LegacySupply";
            order.setError(code, message);
            repository.save(order);
            log.warn("Reorder left PENDING for retry: product={} buyerRef={} reason={}",
                    order.getProductId(), order.getBuyerRef(), message);
            return new SupplierOrderResult(order.getBuyerRef(), SupplierOrderStatus.PENDING);
        }

        // A genuine business rejection (bad SKU, bad qty, bad BuyerRef,
        // malformed request). Retrying the identical order would not help -
        // this means our config or mapping is wrong and needs a human fix.
        String code = outcome.error != null ? outcome.error.code() : "UNKNOWN";
        String message = outcome.error != null ? outcome.error.message() : "Unrecognized response";
        order.setError(code, message);
        order.setStatus(SupplierOrderStatus.FAILED);
        repository.save(order);
        log.error("Reorder REJECTED by LegacySupply: product={} buyerRef={} code={} message={}",
                order.getProductId(), order.getBuyerRef(), code, message);
        return new SupplierOrderResult(order.getBuyerRef(), SupplierOrderStatus.FAILED);
    }

    static SupplierOrderStatus mapStatusCode(int code) {
        return switch (code) {
            case 10 -> SupplierOrderStatus.ACCEPTED;
            case 20 -> SupplierOrderStatus.PICKING;
            case 30 -> SupplierOrderStatus.SHIPPED;
            case 40 -> SupplierOrderStatus.DELIVERED;
            default -> {
                log.warn("Unrecognized LegacySupply StatusCode {} - leaving order unmapped", code);
                yield SupplierOrderStatus.PENDING;
            }
        };
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }
}
