package edu.cit.alvarado.channel;

import java.time.Instant;

/** Snapshot of the marketplace channel, in our own words. */
public record ChannelStatus(
        String instanceId,
        Instant startedAt,
        boolean live,
        Instant lastHeartbeat,
        Instant lastFeedRead,
        long feedCursor,
        long ordersReceived,
        long accepted,
        long rejected,
        long backordered) {
}
