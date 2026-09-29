package edu.cit.alvarado.supplier;

/**
 * What placing/checking a reorder produced, in our own vocabulary - never
 * LegacySupply's raw status codes or field names.
 */
public record SupplierOrderResult(String buyerRef, SupplierOrderStatus status) {
}
