package edu.cit.alvarado.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * The single row that remembers how far we have read the Tiangge order
 * feed. Stored in the database (not in memory) so a restarted app carries
 * on from here instead of re-reading the feed from the beginning. It is
 * updated in the SAME transaction as the order an event created, so the
 * two can never disagree after a crash.
 */
@Entity
@Table(name = "tiangge_feed_cursor")
class FeedCursor {

    static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    @Column(name = "last_seq", nullable = false)
    private long lastSeq;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected FeedCursor() {
        // JPA
    }

    FeedCursor(long lastSeq) {
        this.id = SINGLETON_ID;
        this.lastSeq = lastSeq;
        this.updatedAt = OffsetDateTime.now();
    }

    long getLastSeq() {
        return lastSeq;
    }

    /** Only ever moves forward - a cursor reset would mean re-reading old events. */
    void advanceTo(long seq) {
        if (seq > lastSeq) {
            lastSeq = seq;
            updatedAt = OffsetDateTime.now();
        }
    }
}
