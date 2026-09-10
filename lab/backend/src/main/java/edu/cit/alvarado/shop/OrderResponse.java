package edu.cit.alvarado.shop;

public record OrderResponse(String status, String reason, InventoryView inventory) {
}
