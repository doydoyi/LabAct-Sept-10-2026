package edu.cit.alvarado.shop;

import edu.cit.alvarado.inventory.InventoryItem;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The only HTTP entry point in the app. This is the "external client
 * integration via REST" style - everything upstream of this (Order calling
 * Inventory) stays in-process.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public OrderResponse placeOrder(@RequestBody OrderRequest request) {
        Order order = orderService.placeOrder(request.productId(), request.quantity());
        InventoryItem item = orderService.currentInventory(request.productId());

        InventoryView view = new InventoryView(item.getProductId(), item.getName(), item.getStock());
        return new OrderResponse(order.getStatus().name(), order.getReason(), view);
    }
}
