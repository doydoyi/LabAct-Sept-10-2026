package edu.cit.alvarado.shop.event;

/** Published by OrderService when a confirmed order is cancelled and restocked. */
public record OrderCancelledEvent(Long orderId) {
}
