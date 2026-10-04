package edu.cit.alvarado.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.alvarado.instance.AppInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * The only class that speaks HTTP to Tiangge. Adds the three required
 * headers (X-Client-Id, Authorization, X-Client-Instance) to every call,
 * converts JSON both ways, and retries the calls Tiangge documents as safe
 * to retry. Never throws for HTTP/transport problems: callers get a Result
 * and decide.
 */
@Component
class TianggeClient {

    private static final Logger log = LoggerFactory.getLogger(TianggeClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final TianggeProperties properties;
    private final AppInstance appInstance;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    TianggeClient(TianggeProperties properties, AppInstance appInstance, ObjectMapper objectMapper) {
        this.properties = properties;
        this.appInstance = appInstance;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()
                || properties.getClientId() == null || properties.getClientId().isBlank()) {
            log.error("Tiangge credentials missing - set LS_CLIENT_ID and LS_API_KEY environment variables");
        }
    }

    /** What one call produced. status -1 means no HTTP response at all. */
    record Result(int status, String body, String errorCode, String errorMessage) {

        boolean ok() {
            return status >= 200 && status < 300;
        }

        /** Timeouts, 5xx and 429: Tiangge may still have recorded it - retry the same content. */
        boolean retryable() {
            return status == -1 || status == 429 || status >= 500;
        }

        String describe() {
            return status == -1 ? "no response (" + errorMessage + ")"
                    : "HTTP " + status + (errorCode != null ? " " + errorCode + ": " + errorMessage : "");
        }
    }

    // ---------- endpoints ----------

    Result heartbeat() {
        TianggeJson.Heartbeat body = new TianggeJson.Heartbeat(
                properties.getAppName(), appInstance.startedAt().toString(), appInstance.uptimeSeconds());
        return call("POST", "/instances/heartbeat", body, 1);
    }

    Result putListings(List<TianggeJson.Listing> listings) {
        return call("PUT", "/listings", listings, 3);
    }

    Result putStock(List<TianggeJson.StockEntry> stock) {
        return call("PUT", "/stock", stock, 3);
    }

    Result getFeed(long after, int limit) {
        return call("GET", "/feed?after=" + after + "&limit=" + limit, null, 2);
    }

    Result postDecision(String orderId, TianggeJson.Decision decision) {
        return call("POST", "/orders/" + orderId + "/decision", decision, 3);
    }

    Result postResolution(String orderId, TianggeJson.Resolution resolution) {
        return call("POST", "/orders/" + orderId + "/resolution", resolution, 3);
    }

    Result postCancellation(String orderId, TianggeJson.CancellationConfirmation confirmation) {
        return call("POST", "/orders/" + orderId + "/cancellation", confirmation, 3);
    }

    <T> T read(Result result, Class<T> type) {
        try {
            return objectMapper.readValue(result.body(), type);
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected Tiangge response: " + result.body(), e);
        }
    }

    // ---------- transport ----------

    private Result call(String method, String path, Object body, int maxAttempts) {
        Result result = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            result = once(method, path, body);
            if (!result.retryable()) {
                return result;
            }
            if (attempt < maxAttempts) {
                log.warn("Tiangge {} {} attempt {}/{} failed: {} - retrying",
                        method, path, attempt, maxAttempts, result.describe());
                sleep(500L * (1L << (attempt - 1))); // 0.5s, 1s, 2s ...
            }
        }
        return result;
    }

    private Result once(String method, String path, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + path))
                    .timeout(REQUEST_TIMEOUT)
                    .header("X-Client-Id", nullToEmpty(properties.getClientId()))
                    .header("Authorization", "Bearer " + nullToEmpty(properties.getApiKey()))
                    .header(AppInstance.HEADER, appInstance.id())
                    .header("Accept", "application/json");
            if (body != null) {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return new Result(status, response.body(), null, null);
            }
            TianggeJson.Error error = parseError(response.body());
            return new Result(status, response.body(),
                    error != null ? error.error() : null, error != null ? error.message() : response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(-1, null, null, "interrupted");
        } catch (Exception e) {
            return new Result(-1, null, null, e.getClass().getSimpleName());
        }
    }

    private TianggeJson.Error parseError(String body) {
        try {
            return body == null || body.isBlank() ? null : objectMapper.readValue(body, TianggeJson.Error.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
