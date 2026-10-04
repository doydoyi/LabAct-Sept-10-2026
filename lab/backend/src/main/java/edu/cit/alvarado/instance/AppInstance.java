package edu.cit.alvarado.instance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Identity of this running copy of the application. A fresh UUID is
 * generated every time the app starts (never persisted), and every outbound
 * call to Tiangge and LegacySupply carries it as X-Client-Instance so both
 * systems can tell which running copy made the call.
 */
@Component
public class AppInstance {

    public static final String HEADER = "X-Client-Instance";

    private static final Logger log = LoggerFactory.getLogger(AppInstance.class);

    private final UUID id = UUID.randomUUID();
    private final Instant startedAt = Instant.now();

    public AppInstance() {
        log.info("==== Application instance ID: {} (started {}) ====", id, startedAt);
    }

    public String id() {
        return id.toString();
    }

    public Instant startedAt() {
        return startedAt;
    }

    public long uptimeSeconds() {
        return Duration.between(startedAt, Instant.now()).getSeconds();
    }
}
