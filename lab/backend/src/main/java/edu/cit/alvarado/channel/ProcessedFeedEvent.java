package edu.cit.alvarado.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * One row per Tiangge eventId we have already handled. Tiangge delivers at
 * least once (a redelivered event has a NEW seq but the SAME eventId), so
 * this table - not the cursor - is what guarantees each event is processed
 * exactly once.
 */
@Entity
@Table(name = "tiangge_processed_events")
class ProcessedFeedEvent {

    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(nullable = false)
    private long seq;

    @Column(nullable = false)
    private String type;

    @Column(name = "tiangge_order_id")
    private String tianggeOrderId;

    @Column(name = "processed_at", nullable = false)
    private OffsetDateTime processedAt;

    protected ProcessedFeedEvent() {
        // JPA
    }

    ProcessedFeedEvent(String eventId, long seq, String type, String tianggeOrderId) {
        this.eventId = eventId;
        this.seq = seq;
        this.type = type;
        this.tianggeOrderId = tianggeOrderId;
        this.processedAt = OffsetDateTime.now();
    }
}
