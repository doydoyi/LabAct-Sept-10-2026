package edu.cit.alvarado.shop;

public enum OrderStatus {
    CONFIRMED,
    REJECTED,
    CANCELLED,
    /** Accepted but not yet reserved: waits for stock that is already on order. */
    BACKORDERED
}
