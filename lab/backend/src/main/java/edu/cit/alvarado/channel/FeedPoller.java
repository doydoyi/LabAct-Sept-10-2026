package edu.cit.alvarado.channel;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Task 4: nobody tells us about new orders - we ask. Every few seconds
 * (app.tiangge.feed-poll-ms) this reads the order feed from the stored
 * cursor onward and hands each event, oldest first, to OrderFeedProcessor.
 * After a restart it simply continues from the stored cursor, reading
 * pages until it has caught up with everything that arrived while we were
 * down.
 *
 * If an event fails, we stop at it (the cursor stays before it) and try it
 * again on the next tick - we never skip past an order we haven't decided.
 */
@Component
class FeedPoller {

    private static final Logger log = LoggerFactory.getLogger(FeedPoller.class);
    private static final int MAX_PAGES_PER_TICK = 20;

    private final TianggeClient client;
    private final OrderFeedProcessor processor;
    private final TianggeProperties properties;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "tiangge-feed"));
    private volatile Instant lastSuccessfulRead;

    FeedPoller(TianggeClient client, OrderFeedProcessor processor, TianggeProperties properties) {
        this.client = client;
        this.processor = processor;
        this.properties = properties;
    }

    void start() {
        log.info("Order feed polling started from stored cursor {} (every {} ms)",
                processor.currentCursor(), properties.getFeedPollMs());
        executor.scheduleWithFixedDelay(this::tick, 0, properties.getFeedPollMs(), TimeUnit.MILLISECONDS);
    }

    private void tick() {
        try {
            processor.retryOutstanding();
            readFeed();
            processor.resolveBackorders();
        } catch (Throwable t) {
            // Never let one bad tick kill the scheduled loop.
            log.error("Feed tick failed - will try again in {} ms", properties.getFeedPollMs(), t);
        }
    }

    private void readFeed() {
        for (int page = 0; page < MAX_PAGES_PER_TICK; page++) {
            long cursor = processor.currentCursor();
            TianggeClient.Result result = client.getFeed(cursor, properties.getFeedBatchSize());
            if (!result.ok()) {
                log.warn("Could not read order feed after {}: {}", cursor, result.describe());
                return;
            }
            lastSuccessfulRead = Instant.now();
            TianggeJson.FeedPage feed = client.read(result, TianggeJson.FeedPage.class);
            if (feed.events() == null || feed.events().isEmpty()) {
                return; // caught up
            }
            log.info("Feed: {} new event(s) after cursor {}", feed.events().size(), cursor);

            for (TianggeJson.FeedEvent event : feed.events()) {
                if (event.seq() <= cursor) {
                    continue;
                }
                try {
                    processor.handle(event);
                } catch (Exception e) {
                    log.error("Processing feed seq={} eventId={} ({} {}) failed - will retry it next tick",
                            event.seq(), event.eventId(), event.type(), event.orderId(), e);
                    return; // keep the cursor before this event
                }
            }
            if (feed.nextCursor() != null) {
                processor.advanceCursor(feed.nextCursor());
            }
            if (feed.events().size() < properties.getFeedBatchSize()) {
                return; // that was the last page
            }
        }
    }

    Instant lastSuccessfulRead() {
        return lastSuccessfulRead;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
