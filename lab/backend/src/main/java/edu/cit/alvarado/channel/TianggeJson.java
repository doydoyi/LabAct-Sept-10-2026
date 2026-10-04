package edu.cit.alvarado.channel;

import java.util.List;

/**
 * Tiangge's JSON shapes, exactly as the Tiangge manual names them. These
 * never leave this package: the translator turns them into our own
 * LineItem / Order vocabulary before anything else sees them.
 */
final class TianggeJson {

    private TianggeJson() {
    }

    // ---------- requests ----------

    record Heartbeat(String appName, String startedAt, long uptimeSeconds) {
    }

    record Listing(String sellerSku, String title, String supplierSku) {
    }

    record StockEntry(String sellerSku, int available) {
    }

    record Decision(String decision, String shopOrderId, String reason) {
    }

    record Resolution(String status) {
    }

    record CancellationConfirmation(boolean restocked) {
    }

    // ---------- responses ----------

    record HeartbeatAck(String serverTime, Integer nextHeartbeatSeconds) {
    }

    record FeedPage(List<FeedEvent> events, Long nextCursor) {
    }

    record FeedEvent(long seq, String eventId, String type, String orderId,
                     String placedAt, String decisionDeadline, List<FeedLine> lines,
                     String cancelledAt, String confirmDeadline) {
    }

    record FeedLine(String sellerSku, int qty) {
    }

    record Error(String error, String message) {
    }

    static final String ORDER_PLACED = "ORDER_PLACED";
    static final String ORDER_CANCELLED = "ORDER_CANCELLED";
}
