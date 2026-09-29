package edu.cit.alvarado.supplier;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * All session handling, XML transport, and retry/timeout behavior lives
 * here. Nothing outside this class ever sees an HTTP status code, an XML
 * string, or a session token. Sessions are obtained automatically on first
 * use and refreshed automatically the moment LegacySupply stops accepting
 * the current one - callers never think about sessions at all.
 */
@Component
class LegacySupplyClient {

    private static final int CONNECT_TIMEOUT_SECONDS = 3;
    private static final int MAX_ATTEMPTS = 3;

    private final SupplierProperties properties;
    private final HttpClient httpClient;

    /** Current session token. Reset to null whenever LegacySupply tells us
     *  it's no longer valid, so the next call re-authenticates automatically. */
    private volatile String sessionToken;

    LegacySupplyClient(SupplierProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
    }

    /** Outcome of one call, whatever happened - callers branch on this
     *  instead of catching exceptions for ordinary business rejections. */
    static final class Outcome {
        final boolean transportFailure;   // true = timeout/IOException, no HTTP response at all
        final int httpStatus;             // meaningless if transportFailure
        final String body;                // raw response body, meaningless if transportFailure
        final LegacySupplyXml.LsError error; // non-null only when LegacySupply returned an LSError

        private Outcome(boolean transportFailure, int httpStatus, String body, LegacySupplyXml.LsError error) {
            this.transportFailure = transportFailure;
            this.httpStatus = httpStatus;
            this.body = body;
            this.error = error;
        }

        static Outcome transportFailure() {
            return new Outcome(true, -1, null, null);
        }

        static Outcome of(int status, String body) {
            LegacySupplyXml.LsError error = LegacySupplyXml.looksLikeError(body)
                    ? LegacySupplyXml.parseLsError(body)
                    : null;
            return new Outcome(false, status, body, error);
        }

        boolean isSuccess() {
            return !transportFailure && httpStatus >= 200 && httpStatus < 300;
        }

        /** 401 with a session-related error - the caller should re-auth and retry once. */
        boolean isSessionProblem() {
            return !transportFailure && httpStatus == 401 && error != null
                    && (error.code().equals("E-AUTH-02") || error.code().equals("E-AUTH-03")
                        || error.code().equals("E-AUTH-07"));
        }

        /** Transient server-side trouble worth retrying with backoff. */
        boolean isRetryableServerError() {
            return transportFailure
                    || httpStatus == 503
                    || (error != null && (error.code().equals("E-SYS-50") || error.code().equals("E-SYS-99")));
        }

        /** Quota exceeded - retrying immediately would make it worse; the
         *  caller should leave the order PENDING for a later scheduled pass. */
        boolean isRateLimited() {
            return !transportFailure && httpStatus == 429;
        }
    }

    /** Places one purchase order. requestId is sent as X-Request-Id so a
     *  retried call with the same id is never processed twice by LegacySupply. */
    Outcome placeOrder(String supplierSku, int qty, String buyerRef, String requestId) {
        String body = LegacySupplyXml.buildPurchaseOrder(supplierSku, qty, buyerRef);
        return callWithSessionAndRetry("POST", "/purchase-orders", body, requestId);
    }

    /** Fetches the current status of a previously placed order. */
    Outcome getOrderStatus(String poNumber) {
        return callWithSessionAndRetry("GET", "/purchase-orders/" + poNumber, null, null);
    }

    /** Fetches the partner's catalog. Used by CatalogStartupCheck and by
     *  probe.sh (indirectly, via curl) to discover real SupplierSku values -
     *  not on the hot path of placing/polling orders. */
    Outcome getCatalog() {
        return callWithSessionAndRetry("GET", "/catalog", null, null);
    }

    // ---------- session + retry machinery ----------

    private Outcome callWithSessionAndRetry(String method, String path, String body, String requestId) {
        ensureSession();

        Outcome outcome = attemptWithRetries(method, path, body, requestId);

        if (outcome.isSessionProblem()) {
            // Session died between calls (or was never valid) - get a new
            // one and retry exactly once. No manual token handling required
            // anywhere else in the codebase.
            sessionToken = null;
            ensureSession();
            outcome = attemptWithRetries(method, path, body, requestId);
        }

        return outcome;
    }

    private Outcome attemptWithRetries(String method, String path, String body, String requestId) {
        Outcome outcome = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            outcome = doCall(method, path, body, requestId, true);

            if (!outcome.isRetryableServerError()) {
                return outcome; // success, session problem, business error, or rate limit - no retry
            }
            if (attempt < MAX_ATTEMPTS) {
                sleep(500L * attempt);
            }
        }
        return outcome;
    }

    private void ensureSession() {
        if (sessionToken != null) {
            return;
        }
        String authBody = LegacySupplyXml.buildAuthRequest(properties.getClientId(), properties.getApiKey());
        Outcome outcome = doCall("POST", "/auth/token", authBody, null, false);
        if (outcome.isSuccess()) {
            sessionToken = LegacySupplyXml.parseAuthResponse(outcome.body).sessionToken();
        }
        // If auth itself fails, sessionToken stays null and the subsequent
        // call will simply fail too - that failure is what the caller sees
        // and logs, so nothing is silently swallowed here.
    }

    private Outcome doCall(String method, String path, String body, String requestId, boolean withSession) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getBaseUrl() + path))
                    .timeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS));

            if (withSession && sessionToken != null) {
                builder.header("X-LS-Session", sessionToken);
            }
            if (requestId != null) {
                builder.header("X-Request-Id", requestId);
            }

            if (body != null) {
                builder.header("Content-Type", "application/xml");
                builder.method(method, HttpRequest.BodyPublishers.ofString(body));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return Outcome.of(response.statusCode(), response.body());

        } catch (Exception e) {
            // Covers connect timeout, read timeout, and any other transport
            // failure - treated as retryable, never as a business rejection.
            return Outcome.transportFailure();
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
