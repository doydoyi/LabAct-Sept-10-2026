package edu.cit.alvarado.shop;

public class OrderAlreadyCancelledException extends RuntimeException {

    public OrderAlreadyCancelledException(Long orderId) {
        super("Order already cancelled: " + orderId);
    }
}
