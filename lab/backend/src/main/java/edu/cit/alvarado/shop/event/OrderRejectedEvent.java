package edu.cit.alvarado.shop.event;

/** Published by OrderService when an order is rejected (no items reserved). */
public record OrderRejectedEvent(Long orderId) {
}
