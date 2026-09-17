package edu.cit.alvarado.notification;

import edu.cit.alvarado.inventory.event.LowStockEvent;
import edu.cit.alvarado.shop.event.OrderCancelledEvent;
import edu.cit.alvarado.shop.event.OrderPlacedEvent;
import edu.cit.alvarado.shop.event.OrderRejectedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * The Notification module's only coupling to Order/Inventory is these four
 * event record types - it never imports OrderService or InventoryService.
 * Listeners run synchronously (no @Async): see the README for why - this
 * lab's request volume is trivial, and synchronous listeners keep the
 * notification write inside the SAME transaction as the order/inventory
 * change that caused it, so there's no risk of an order being confirmed
 * while its notification silently fails to write.
 */
@Service
class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;

    NotificationServiceImpl(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @EventListener
    void onOrderPlaced(OrderPlacedEvent event) {
        save("Order O" + event.orderId() + " confirmed");
    }

    @EventListener
    void onOrderRejected(OrderRejectedEvent event) {
        save("Order O" + event.orderId() + " rejected");
    }

    @EventListener
    void onOrderCancelled(OrderCancelledEvent event) {
        save("Order O" + event.orderId() + " cancelled");
    }

    @EventListener
    void onLowStock(LowStockEvent event) {
        save("Low stock alert: " + event.productId() + " has " + event.remainingStock()
                + " unit(s) left (threshold " + event.threshold() + ")");
    }

    private void save(String message) {
        notificationRepository.save(new Notification(message, OffsetDateTime.now()));
    }

    @Override
    public List<Notification> listAll() {
        return notificationRepository.findAllByOrderByCreatedAtDesc();
    }
}
