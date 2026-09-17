package edu.cit.alvarado.shop.event;

/**
 * Published by OrderService when an order is confirmed. This is the only
 * thing the Notification module knows about the Order module - it depends
 * on this event class, never on OrderService itself.
 */
public record OrderPlacedEvent(Long orderId) {
}
