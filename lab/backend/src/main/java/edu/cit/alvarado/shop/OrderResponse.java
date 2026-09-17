package edu.cit.alvarado.shop;

import java.util.List;

public record OrderResponse(String status, String reason, List<ItemOutcome> items, List<InventoryView> inventory) {
}
