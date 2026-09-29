package edu.cit.alvarado.supplier;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * All LegacySupply-specific configuration lives here, bound from
 * application.properties under the "app.supplier" prefix. Nothing here is
 * public API for other modules - it's read only by classes inside this
 * package.
 *
 * The catalog map is how our product IDs (P100, P200, P300...) translate to
 * LegacySupply's SupplierSku and PackSize. This CANNOT be filled in until
 * you have actually called GET /catalog once (see probe.sh / SETUP.md) -
 * it is partner-specific and not guessable.
 */
@Component
@ConfigurationProperties(prefix = "app.supplier")
class SupplierProperties {

    private String baseUrl;
    private String apiKey;
    private String clientId;

    /** Stock level a reorder tries to bring a product back up to. */
    private int reorderTargetLevel = 20;

    /** How often the delivery-status polling job runs, in milliseconds. */
    private long pollIntervalMs = 120_000;

    /** How often the PENDING-order retry job runs, in milliseconds. */
    private long retryIntervalMs = 60_000;

    /** productId -> CatalogEntry (SupplierSku + PackSize). */
    private Map<String, CatalogEntry> catalog = new HashMap<>();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public int getReorderTargetLevel() {
        return reorderTargetLevel;
    }

    public void setReorderTargetLevel(int reorderTargetLevel) {
        this.reorderTargetLevel = reorderTargetLevel;
    }

    public long getPollIntervalMs() {
        return pollIntervalMs;
    }

    public void setPollIntervalMs(long pollIntervalMs) {
        this.pollIntervalMs = pollIntervalMs;
    }

    public long getRetryIntervalMs() {
        return retryIntervalMs;
    }

    public void setRetryIntervalMs(long retryIntervalMs) {
        this.retryIntervalMs = retryIntervalMs;
    }

    public Map<String, CatalogEntry> getCatalog() {
        return catalog;
    }

    public void setCatalog(Map<String, CatalogEntry> catalog) {
        this.catalog = catalog;
    }

    public static class CatalogEntry {
        private String sku;
        private int packSize;

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public int getPackSize() {
            return packSize;
        }

        public void setPackSize(int packSize) {
            this.packSize = packSize;
        }
    }
}
