package edu.cit.alvarado.supplier;

/**
 * Public contract of the Supplier module. This is the ONLY supplier type
 * other modules may depend on. Nothing about LegacySupply - not its XML
 * shapes, its SupplierSku values, its session tokens, its HTTP client, or
 * its status codes - is visible from outside this package. Everything
 * else in this package is package-private on purpose.
 */
public interface SupplierGateway {

    /**
     * Places a reorder for the given product. The gateway decides how many
     * units to order (based on configured reorder target level) and how to
     * translate that into LegacySupply's own unit of measure - callers
     * never need to know about packs, SKUs, or LegacySupply's XML.
     *
     * This never throws for ordinary supplier failures (rejected orders,
     * LegacySupply being down, etc.) - it always returns a result, with the
     * failure reflected in the result's status. It may only throw if the
     * given productId has no supplier mapping configured at all, which is
     * a configuration bug, not a runtime supplier failure.
     */
    SupplierOrderResult reorder(String productId, int currentStock);
}
