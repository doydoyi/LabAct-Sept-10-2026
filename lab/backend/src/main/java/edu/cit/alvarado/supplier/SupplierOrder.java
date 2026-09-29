package edu.cit.alvarado.supplier;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * One row per reorder attempt. Created as PENDING before we ever call
 * LegacySupply, so a reorder is never lost even if the call never
 * completes. buyerRef and requestId together are what make retries and
 * duplicate submission impossible - see LegacySupplyGatewayImpl.
 */
@Entity
@Table(name = "supplier_orders")
class SupplierOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private String productId;

    /** "RO-" + this row's id. Unique per reorder, set right after insert. */
    @Column(name = "buyer_ref", unique = true)
    private String buyerRef;

    /** Idempotency key sent as X-Request-Id on every attempt for this row. */
    @Column(name = "request_id", nullable = false, unique = true)
    private String requestId;

    @Column(name = "supplier_sku", nullable = false)
    private String supplierSku;

    /** Quantity in LegacySupply's unit of measure (packs), not raw units. */
    @Column(nullable = false)
    private int qty;

    /** Units per pack, copied from config at creation time so a later
     *  config change can't silently change how much a past order restocks. */
    @Column(name = "pack_size", nullable = false)
    private int packSize;

    /** LegacySupply's Uom string (e.g. "CS"), once known. */
    private String uom;

    @Column(name = "po_number")
    private String poNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupplierOrderStatus status;

    /** Raw LegacySupply StatusCode, kept only for debugging/audit. */
    @Column(name = "legacy_status_code")
    private Integer legacyStatusCode;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SupplierOrder() {
        // JPA
    }

    SupplierOrder(String productId, String requestId, String supplierSku, int qty, int packSize) {
        this.productId = productId;
        this.requestId = requestId;
        this.supplierSku = supplierSku;
        this.qty = qty;
        this.packSize = packSize;
        this.status = SupplierOrderStatus.PENDING;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    Long getId() {
        return id;
    }

    String getProductId() {
        return productId;
    }

    String getBuyerRef() {
        return buyerRef;
    }

    void setBuyerRef(String buyerRef) {
        this.buyerRef = buyerRef;
        touch();
    }

    String getRequestId() {
        return requestId;
    }

    String getSupplierSku() {
        return supplierSku;
    }

    int getQty() {
        return qty;
    }

    int getPackSize() {
        return packSize;
    }

    String getUom() {
        return uom;
    }

    void setUom(String uom) {
        this.uom = uom;
        touch();
    }

    String getPoNumber() {
        return poNumber;
    }

    void setPoNumber(String poNumber) {
        this.poNumber = poNumber;
        touch();
    }

    SupplierOrderStatus getStatus() {
        return status;
    }

    void setStatus(SupplierOrderStatus status) {
        this.status = status;
        touch();
    }

    Integer getLegacyStatusCode() {
        return legacyStatusCode;
    }

    void setLegacyStatusCode(Integer legacyStatusCode) {
        this.legacyStatusCode = legacyStatusCode;
        touch();
    }

    void setError(String errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        touch();
    }

    private void touch() {
        this.updatedAt = OffsetDateTime.now();
    }
}
