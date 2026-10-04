package edu.cit.alvarado.shop;

import edu.cit.alvarado.inventory.InventoryItem;
import edu.cit.alvarado.inventory.InventoryService;
import edu.cit.alvarado.inventory.ReservationResult;
import edu.cit.alvarado.shop.event.OrderCancelledEvent;
import edu.cit.alvarado.shop.event.OrderPlacedEvent;
import edu.cit.alvarado.shop.event.OrderRejectedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Order module orchestration. Depends only on the public InventoryService
 * interface (in-process call, same JVM, no network) and publishes domain
 * events instead of calling the Notification module directly - OrderService
 * has no idea Notification even exists.
 */
@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(InventoryService inventoryService,
                         OrderRepository orderRepository,
                         ApplicationEventPublisher eventPublisher) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * All-or-nothing: every line item is validated against current stock
     * BEFORE any reservation happens. If a single item can't be satisfied,
     * the whole order is rejected and InventoryService.reserve() is never
     * called for any item - so nothing is partially reserved.
     */
    @Transactional
    public OrderResult placeOrder(List<LineItem> lineItems) {
        List<ItemOutcome> validationOutcomes = new ArrayList<>();
        boolean allAvailable = true;

        for (LineItem li : lineItems) {
            InventoryItem inv = inventoryService.getItem(li.productId());
            boolean ok = inv.getStock() >= li.quantity();
            validationOutcomes.add(new ItemOutcome(li.productId(), ok ? "AVAILABLE" : "INSUFFICIENT_STOCK"));
            if (!ok) {
                allAvailable = false;
            }
        }

        if (!allAvailable) {
            Order order = buildOrder(OrderStatus.REJECTED,
                    "One or more items exceeded available stock; no items were reserved", lineItems);
            Order saved = orderRepository.save(order);
            eventPublisher.publishEvent(new OrderRejectedEvent(saved.getOrderId()));
            return new OrderResult(saved, validationOutcomes);
        }

        // Validation passed for every item - now actually reserve each one.
        // If a reserve() unexpectedly fails here (e.g. a concurrent order
        // raced us between validation and reservation), throwing aborts the
        // whole @Transactional method, rolling back any reserves already
        // made in this loop, since InventoryServiceImpl.reserve() joins the
        // same transaction by default (propagation REQUIRED).
        for (LineItem li : lineItems) {
            ReservationResult result = inventoryService.reserve(li.productId(), li.quantity());
            if (!result.approved()) {
                throw new IllegalStateException(
                        "Stock for " + li.productId() + " changed concurrently; aborting order");
            }
        }

        Order order = buildOrder(OrderStatus.CONFIRMED, "All items reserved", lineItems);
        Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderPlacedEvent(saved.getOrderId()));

        List<ItemOutcome> confirmedOutcomes = lineItems.stream()
                .map(li -> new ItemOutcome(li.productId(), "RESERVED"))
                .toList();
        return new OrderResult(saved, confirmedOutcomes);
    }

    /**
     * Same all-or-nothing rule as placeOrder(), for callers that can wait
     * for stock. If every line can be reserved now the order is CONFIRMED
     * exactly as placeOrder() would. If not, the lines that are short are
     * handed to backorderAllowed: when it answers true the order is saved as
     * BACKORDERED (nothing reserved yet - see fulfillBackorder()), otherwise
     * it is REJECTED.
     *
     * The Order module has no opinion on WHEN backordering is acceptable
     * (that depends on things like open supplier purchase orders, which the
     * Order module deliberately knows nothing about) - the caller decides.
     */
    @Transactional
    public Order placeOrderOrBackorder(List<LineItem> lineItems, Predicate<List<LineItem>> backorderAllowed) {
        List<LineItem> merged = mergeByProduct(lineItems);
        List<LineItem> shortItems = findShortItems(merged);

        if (shortItems.isEmpty()) {
            return placeOrder(merged).order();
        }

        if (backorderAllowed.test(shortItems)) {
            Order order = buildOrder(OrderStatus.BACKORDERED,
                    "Waiting for restock of " + describe(shortItems), merged);
            return orderRepository.save(order);
        }

        Order order = buildOrder(OrderStatus.REJECTED,
                "One or more items exceeded available stock; no items were reserved", merged);
        Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderRejectedEvent(saved.getOrderId()));
        return saved;
    }

    /**
     * Tries to turn a BACKORDERED order into a CONFIRMED one, all-or-nothing:
     * either every line is reserved now, or nothing is and the order stays
     * BACKORDERED. Orders that are not (or no longer) backordered are
     * returned unchanged.
     */
    @Transactional
    public Order fulfillBackorder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.getStatus() != OrderStatus.BACKORDERED) {
            return order;
        }

        List<LineItem> lineItems = order.getItems().stream()
                .map(oi -> new LineItem(oi.getProductId(), oi.getQuantity()))
                .toList();
        if (!findShortItems(lineItems).isEmpty()) {
            return order;
        }

        for (LineItem li : lineItems) {
            ReservationResult result = inventoryService.reserve(li.productId(), li.quantity());
            if (!result.approved()) {
                throw new IllegalStateException(
                        "Stock for " + li.productId() + " changed concurrently; aborting backorder fill");
            }
        }

        order.setStatus(OrderStatus.CONFIRMED);
        order.setReason("Backorder filled after restock; all items reserved");
        Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderPlacedEvent(saved.getOrderId()));
        return saved;
    }

    /** Lines (merged per product) that current stock cannot cover. */
    public List<LineItem> shortItems(List<LineItem> lineItems) {
        return findShortItems(mergeByProduct(lineItems));
    }

    public Optional<Order> findOrder(Long orderId) {
        return orderRepository.findById(orderId);
    }

    @Transactional
    public Order cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new OrderAlreadyCancelledException(orderId);
        }

        // Only a CONFIRMED order actually holds a reservation to give back.
        // A REJECTED order never reserved anything, so there is nothing to restock.
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            for (OrderItem item : order.getItems()) {
                inventoryService.restock(item.getProductId(), item.getQuantity());
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        Order saved = orderRepository.save(order);
        eventPublisher.publishEvent(new OrderCancelledEvent(saved.getOrderId()));
        return saved;
    }

    public List<Order> listOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<InventoryItem> currentInventory() {
        return inventoryService.listAll();
    }

    private List<LineItem> findShortItems(List<LineItem> lineItems) {
        return lineItems.stream()
                .filter(li -> inventoryService.getItem(li.productId()).getStock() < li.quantity())
                .toList();
    }

    /** Two lines for the same product are checked as one combined quantity. */
    private static List<LineItem> mergeByProduct(List<LineItem> lineItems) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (LineItem li : lineItems) {
            totals.merge(li.productId(), li.quantity(), Integer::sum);
        }
        return totals.entrySet().stream()
                .map(e -> new LineItem(e.getKey(), e.getValue()))
                .toList();
    }

    private static String describe(List<LineItem> items) {
        return String.join(", ", items.stream().map(li -> li.quantity() + "x " + li.productId()).toList());
    }

    private Order buildOrder(OrderStatus status, String reason, List<LineItem> lineItems) {
        Order order = new Order(status, reason, OffsetDateTime.now());
        for (LineItem li : lineItems) {
            order.addItem(new OrderItem(li.productId(), li.quantity()));
        }
        return order;
    }
}
