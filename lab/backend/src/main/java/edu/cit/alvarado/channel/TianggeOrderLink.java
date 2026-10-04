package edu.cit.alvarado.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * Links one Tiangge order to the one order it became in our own Order
 * module, and tracks what we still owe Tiangge (a decision, a backorder
 * resolution, a cancellation confirmation). Each "...Reported" flag stays
 * false until Tiangge has actually acknowledged it, so anything that failed
 * to send (Tiangge down, timeout) is retried on the next feed tick - and
 * survives a restart, since it lives in the database.
 */
@Entity
@Table(name = "tiangge_orders")
class TianggeOrderLink {

    @Id
    @Column(name = "tiangge_order_id")
    private String tianggeOrderId;

    /** Our own orders.order_id; null only if the order could not be created at all. */
    @Column(name = "shop_order_id")
    private Long shopOrderId;

    /** ACCEPTED / REJECTED / BACKORDERED, in Tiangge's vocabulary. */
    @Column(name = "decision")
    private String decision;

    @Column(name = "decision_reason", length = 200)
    private String decisionReason;

    @Column(name = "decision_reported", nullable = false)
    private boolean decisionReported;

    @Column(name = "decision_deadline")
    private OffsetDateTime decisionDeadline;

    /** ACCEPTED / CANCELLED once a backorder has been resolved. */
    @Column(name = "resolution")
    private String resolution;

    @Column(name = "resolution_reported", nullable = false)
    private boolean resolutionReported;

    @Column(name = "cancel_requested", nullable = false)
    private boolean cancelRequested;

    @Column(name = "cancel_confirmed", nullable = false)
    private boolean cancelConfirmed;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected TianggeOrderLink() {
        // JPA
    }

    TianggeOrderLink(String tianggeOrderId, Long shopOrderId, String decision, String decisionReason,
                     OffsetDateTime decisionDeadline) {
        this.tianggeOrderId = tianggeOrderId;
        this.shopOrderId = shopOrderId;
        this.decision = decision;
        this.decisionReason = decisionReason;
        this.decisionDeadline = decisionDeadline;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = createdAt;
    }

    /** A cancellation for an order we never saw placed (should not happen, but must still be confirmed). */
    static TianggeOrderLink unknownOrderCancelled(String tianggeOrderId) {
        TianggeOrderLink link = new TianggeOrderLink(tianggeOrderId, null, null, null, null);
        link.decisionReported = true;
        link.cancelRequested = true;
        return link;
    }

    String getTianggeOrderId() {
        return tianggeOrderId;
    }

    Long getShopOrderId() {
        return shopOrderId;
    }

    String shopOrderRef() {
        return shopOrderId != null ? "SO-" + shopOrderId : "SO-NONE";
    }

    String getDecision() {
        return decision;
    }

    String getDecisionReason() {
        return decisionReason;
    }

    boolean isDecisionReported() {
        return decisionReported;
    }

    void markDecisionReported() {
        this.decisionReported = true;
        touch();
    }

    String getResolution() {
        return resolution;
    }

    void resolve(String resolution) {
        this.resolution = resolution;
        touch();
    }

    boolean isResolutionReported() {
        return resolutionReported;
    }

    void markResolutionReported() {
        this.resolutionReported = true;
        touch();
    }

    boolean isCancelRequested() {
        return cancelRequested;
    }

    void markCancelRequested() {
        this.cancelRequested = true;
        touch();
    }

    boolean isCancelConfirmed() {
        return cancelConfirmed;
    }

    void markCancelConfirmed() {
        this.cancelConfirmed = true;
        touch();
    }

    OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    private void touch() {
        this.updatedAt = OffsetDateTime.now();
    }
}
