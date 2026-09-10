package edu.cit.alvarado.shop;

import edu.cit.alvarado.inventory.InventoryItem;
import edu.cit.alvarado.inventory.InventoryService;
import edu.cit.alvarado.inventory.ReservationResult;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * Order module orchestration. Notice the constructor only accepts the
 * InventoryService interface - InventoryServiceImpl is package-private in
 * edu.cit.alvarado.inventory and therefore cannot even be named here. This
 * is a plain Java method call at runtime (same JVM, same process, no HTTP,
 * no serialization) - that's what "in-process integration" means.
 */
@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;

    public OrderService(InventoryService inventoryService, OrderRepository orderRepository) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
    }

    public Order placeOrder(String productId, int quantity) {
        ReservationResult result = inventoryService.reserve(productId, quantity);

        OrderStatus status = result.approved() ? OrderStatus.CONFIRMED : OrderStatus.REJECTED;
        Order order = new Order(productId, quantity, status, result.reason(), OffsetDateTime.now());
        return orderRepository.save(order);
    }

    public InventoryItem currentInventory(String productId) {
        return inventoryService.getItem(productId);
    }
}
