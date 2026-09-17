package edu.cit.alvarado.shop;

/**
 * outcome is "RESERVED" when the order was confirmed and this item's stock
 * was actually deducted, "AVAILABLE" when validation passed but the whole
 * order was rejected because a DIFFERENT item failed (so nothing, including
 * this item, was reserved), or "INSUFFICIENT_STOCK" when this item itself
 * is what caused the rejection.
 */
public record ItemOutcome(String productId, String outcome) {
}
