package edu.cit.alvarado.shop;

import java.time.OffsetDateTime;
import java.util.List;

public record OrderHistoryView(
        Long orderId,
        String status,
        String reason,
        OffsetDateTime createdAt,
        List<LineItem> items) {
}
