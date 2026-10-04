package edu.cit.alvarado.channel;

import edu.cit.alvarado.inventory.InventoryService;
import edu.cit.alvarado.shop.LineItem;
import edu.cit.alvarado.shop.Order;
import edu.cit.alvarado.shop.OrderStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Anti-corruption layer between Tiangge's vocabulary and ours: Tiangge
 * "lines" with "sellerSku"/"qty" become our LineItem(productId, quantity),
 * and our OrderStatus becomes Tiangge's ACCEPTED/REJECTED/BACKORDERED.
 */
@Component
class TianggeOrderTranslator {

    static final String ACCEPTED = "ACCEPTED";
    static final String REJECTED = "REJECTED";
    static final String BACKORDERED = "BACKORDERED";
    static final String CANCELLED = "CANCELLED";

    private final InventoryService inventoryService;

    TianggeOrderTranslator(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    /** Empty when a line names a product we don't have (or a nonsense quantity). */
    Optional<List<LineItem>> toLineItems(TianggeJson.FeedEvent event) {
        if (event.lines() == null || event.lines().isEmpty()) {
            return Optional.empty();
        }
        List<LineItem> items = event.lines().stream()
                .map(l -> new LineItem(ListingCatalog.toProductId(l.sellerSku()), l.qty()))
                .toList();
        boolean allKnown = items.stream().allMatch(li -> li.quantity() > 0 && productExists(li.productId()));
        return allKnown ? Optional.of(items) : Optional.empty();
    }

    String toDecision(Order order) {
        return toDecision(order.getStatus());
    }

    String toDecision(OrderStatus status) {
        return switch (status) {
            case CONFIRMED -> ACCEPTED;
            case BACKORDERED -> BACKORDERED;
            case REJECTED, CANCELLED -> REJECTED;
        };
    }

    static OffsetDateTime parseTime(String iso) {
        if (iso == null) {
            return null;
        }
        try {
            return Instant.parse(iso).atOffset(ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }

    static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private boolean productExists(String productId) {
        try {
            inventoryService.getItem(productId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
