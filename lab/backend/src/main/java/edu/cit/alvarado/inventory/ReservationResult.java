package edu.cit.alvarado.inventory;

/**
 * Outcome of an inventory reservation attempt. This, InventoryItem, and
 * InventoryService are the only inventory-module types the Order module is
 * allowed to see.
 */
public record ReservationResult(boolean approved, String reason, InventoryItem inventory) {

    public static ReservationResult approved(InventoryItem inventory) {
        return new ReservationResult(true, "Sufficient stock", inventory);
    }

    public static ReservationResult rejected(String reason, InventoryItem inventory) {
        return new ReservationResult(false, reason, inventory);
    }
}
