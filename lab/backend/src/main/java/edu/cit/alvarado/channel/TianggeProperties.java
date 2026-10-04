package edu.cit.alvarado.channel;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Tiangge connection settings, bound from "app.tiangge.*". The API key is
 * never written in application.properties - it comes from the LS_API_KEY
 * environment variable (same key as LegacySupply).
 */
@Component
@ConfigurationProperties(prefix = "app.tiangge")
class TianggeProperties {

    private String baseUrl = "https://legacysupply.onrender.com/tiangge/v1";
    private String clientId;
    private String apiKey;
    private String appName = "alvarado-shop";

    /** Pause between two order-feed reads, in milliseconds. */
    private long feedPollMs = 3_000;

    /** Events requested per feed read (Tiangge allows 1..50). */
    private int feedBatchSize = 50;

    /** A backorder that still cannot be filled after this long is cancelled. */
    private long backorderMaxWaitSeconds = 900;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public long getFeedPollMs() {
        return feedPollMs;
    }

    public void setFeedPollMs(long feedPollMs) {
        this.feedPollMs = feedPollMs;
    }

    public int getFeedBatchSize() {
        return feedBatchSize;
    }

    public void setFeedBatchSize(int feedBatchSize) {
        this.feedBatchSize = feedBatchSize;
    }

    public long getBackorderMaxWaitSeconds() {
        return backorderMaxWaitSeconds;
    }

    public void setBackorderMaxWaitSeconds(long backorderMaxWaitSeconds) {
        this.backorderMaxWaitSeconds = backorderMaxWaitSeconds;
    }
}
