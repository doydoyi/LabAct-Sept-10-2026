package edu.cit.alvarado.shop;

import edu.cit.alvarado.inventory.InventoryItem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public OrderResponse placeOrder(@RequestBody OrderRequest request) {
        OrderResult result = orderService.placeOrder(request.items());
        Order order = result.order();

        List<InventoryView> inventoryViews = toInventoryViews(order);
        return new OrderResponse(order.getStatus().name(), order.getReason(), result.itemOutcomes(), inventoryViews);
    }

    @PostMapping("/{orderId}/cancel")
    public OrderHistoryView cancelOrder(@PathVariable Long orderId) {
        Order order = orderService.cancelOrder(orderId);
        return toHistoryView(order);
    }

    @GetMapping
    public List<OrderHistoryView> listOrders() {
        return orderService.listOrders().stream()
                .map(this::toHistoryView)
                .toList();
    }

    private List<InventoryView> toInventoryViews(Order order) {
        List<InventoryItem> allInventory = orderService.currentInventory();

        return order.getItems().stream()
                .map(OrderItem::getProductId)
                .distinct()
                .map(productId -> allInventory.stream()
                        .filter(item -> item.getProductId().equals(productId))
                        .findFirst()
                        .orElseThrow())
                .map(item -> new InventoryView(item.getProductId(), item.getName(), item.getStock()))
                .toList();
    }

    private OrderHistoryView toHistoryView(Order order) {
        List<LineItem> items = order.getItems().stream()
                .map(oi -> new LineItem(oi.getProductId(), oi.getQuantity()))
                .toList();
        return new OrderHistoryView(order.getOrderId(), order.getStatus().name(), order.getReason(),
                order.getCreatedAt(), items);
    }
}
