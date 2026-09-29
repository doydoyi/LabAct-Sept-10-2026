package edu.cit.alvarado.supplier;

/**
 * Our own reorder lifecycle. LegacySupply's numeric StatusCode (10/20/30/40)
 * is mapped into this enum inside the adapter and polling job - nothing
 * outside this package ever sees the raw code.
 */
public enum SupplierOrderStatus {
    /** Created locally; not yet successfully submitted to LegacySupply. */
    PENDING,
    /** LegacySupply StatusCode 10. */
    ACCEPTED,
    /** LegacySupply StatusCode 20. */
    PICKING,
    /** LegacySupply StatusCode 30. */
    SHIPPED,
    /** LegacySupply StatusCode 40. Triggers inventory restock. */
    DELIVERED,
    /** A business-level rejection (bad SKU, bad qty, bad ref) - retrying
     *  the same order would not help; the mapping/config needs fixing. */
    FAILED
}
