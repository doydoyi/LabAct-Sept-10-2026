package edu.cit.alvarado.channel;

import edu.cit.alvarado.instance.AppInstance;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Task 1: tells Tiangge this instance is alive. The first heartbeat is
 * sent by ChannelStartup before any other Tiangge call; after that one goes
 * out every nextHeartbeatSeconds (30s). It runs on its own thread so a long
 * burst of order processing can never delay it past Tiangge's 90-second
 * offline cutoff. A failed heartbeat is retried after 5 seconds.
 */
@Component
class HeartbeatSender {

    private static final Logger log = LoggerFactory.getLogger(HeartbeatSender.class);
    private static final int DEFAULT_INTERVAL_SECONDS = 30;
    private static final int RETRY_SECONDS = 5;

    private final TianggeClient client;
    private final AppInstance appInstance;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "tiangge-heartbeat"));
    private volatile Instant lastSuccess;

    HeartbeatSender(TianggeClient client, AppInstance appInstance) {
        this.client = client;
        this.appInstance = appInstance;
    }

    /** Sends the first heartbeat synchronously, then keeps beating in the background. */
    void start() {
        int next = beat();
        executor.schedule(this::beatAndReschedule, next, TimeUnit.SECONDS);
    }

    private void beatAndReschedule() {
        int next = DEFAULT_INTERVAL_SECONDS;
        try {
            next = beat();
        } finally {
            executor.schedule(this::beatAndReschedule, next, TimeUnit.SECONDS);
        }
    }

    /** @return seconds until the next heartbeat should go out. */
    private int beat() {
        try {
            TianggeClient.Result result = client.heartbeat();
            if (!result.ok()) {
                log.warn("Heartbeat failed ({}) - retrying in {}s", result.describe(), RETRY_SECONDS);
                return RETRY_SECONDS;
            }
            boolean first = lastSuccess == null;
            lastSuccess = Instant.now();
            TianggeJson.HeartbeatAck ack = client.read(result, TianggeJson.HeartbeatAck.class);
            if (first) {
                log.info("First heartbeat accepted - Tiangge sees instance {}", appInstance.id());
            } else {
                log.debug("Heartbeat ok (instance {}, uptime {}s)", appInstance.id(), appInstance.uptimeSeconds());
            }
            Integer next = ack.nextHeartbeatSeconds();
            return next == null || next <= 0 ? DEFAULT_INTERVAL_SECONDS : Math.min(next, DEFAULT_INTERVAL_SECONDS);
        } catch (Exception e) {
            log.warn("Heartbeat crashed - retrying in {}s", RETRY_SECONDS, e);
            return RETRY_SECONDS;
        }
    }

    Instant lastSuccess() {
        return lastSuccess;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
