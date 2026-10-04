package edu.cit.alvarado.channel;

import edu.cit.alvarado.instance.AppInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Go-live sequence (Tasks 1 and 2), run once when the app starts and before
 * any other startup work:
 *   1. first heartbeat (Tiangge wants it before any other call),
 *   2. publish listings with their LegacySupply mapping (retried until
 *      Tiangge accepts them - a shop with no listings sells nothing),
 *   3. publish the current stock of every listing,
 *   4. start reading the order feed.
 */
@Component
@Order(0)
class ChannelStartup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChannelStartup.class);
    private static final long LISTING_RETRY_MS = 5_000;

    private final AppInstance appInstance;
    private final HeartbeatSender heartbeat;
    private final TianggeClient client;
    private final ListingCatalog catalog;
    private final StockPublisher stockPublisher;
    private final FeedPoller feedPoller;
    private volatile boolean live = false;

    ChannelStartup(AppInstance appInstance, HeartbeatSender heartbeat, TianggeClient client,
                   ListingCatalog catalog, StockPublisher stockPublisher, FeedPoller feedPoller) {
        this.appInstance = appInstance;
        this.heartbeat = heartbeat;
        this.client = client;
        this.catalog = catalog;
        this.stockPublisher = stockPublisher;
        this.feedPoller = feedPoller;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Tiangge channel starting with instance ID {}", appInstance.id());
        heartbeat.start();
        Thread golive = new Thread(this::goLive, "tiangge-golive");
        golive.setDaemon(true);
        golive.start();
    }

    private void goLive() {
        List<TianggeJson.Listing> listings = catalog.listings();
        while (true) {
            TianggeClient.Result result = client.putListings(listings);
            if (result.ok()) {
                log.info("Published {} Tiangge listing(s): {}", listings.size(), listings);
                break;
            }
            log.warn("Publishing listings failed ({}) - retrying in {} ms", result.describe(), LISTING_RETRY_MS);
            if (!sleep(LISTING_RETRY_MS)) {
                return;
            }
        }
        stockPublisher.publishAllNow();
        feedPoller.start();
        live = true;
        log.info("Shop is live on Tiangge (instance {})", appInstance.id());
    }

    boolean isLive() {
        return live;
    }

    private static boolean sleep(long ms) {
        try {
            Thread.sleep(ms);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
