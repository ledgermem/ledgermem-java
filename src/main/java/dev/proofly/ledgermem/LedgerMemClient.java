package dev.proofly.ledgermem;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.proofly.ledgermem.model.SearchInput;
import dev.proofly.ledgermem.model.SearchResult;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/** Official Java client for the LedgerMem API. */
public final class LedgerMemClient {

    private static final String DEFAULT_BASE_URL = "https://api.proofly.dev";
    private static final String USER_AGENT = "ledgermem-java/0.1.0";
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final long RETRY_BASE_DELAY_MS = 200L;
    private static final long RETRY_MAX_DELAY_MS = 5_000L;

    private final String apiKey;
    private final String workspaceId;
    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final MemoriesService memories;
    private final int maxRetries;

    private LedgerMemClient(Builder b) {
        this.apiKey = first(b.apiKey, System.getenv("LEDGERMEM_API_KEY"));
        this.workspaceId = first(b.workspaceId, System.getenv("LEDGERMEM_WORKSPACE_ID"));
        String url = first(b.baseUrl, System.getenv("LEDGERMEM_API_URL"));
        this.baseUrl = stripTrailingSlash(url == null ? DEFAULT_BASE_URL : url);
        this.http = b.httpClient != null
                ? b.httpClient
                : HttpClient.newBuilder().connectTimeout(b.timeout).build();
        this.mapper = new ObjectMapper().disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        this.memories = new MemoriesService(this);
        this.maxRetries = Math.max(0, b.maxRetries);
    }

    public static Builder builder() {
        return new Builder();
    }

    public MemoriesService memories() {
        return memories;
    }

    public SearchResult search(SearchInput input) throws IOException, InterruptedException {
        return request("POST", "/v1/search", Map.of(), input, SearchResult.class);
    }

    <T> T request(String method, String path, Map<String, String> query, Object body, Class<T> responseType)
            throws IOException, InterruptedException {
        URI uri = URI.create(baseUrl + path + buildQuery(query));
        // Pre-serialize the body once so we can resend it cheaply on retry.
        byte[] bodyBytes = body == null ? null : mapper.writeValueAsBytes(body);

        IOException lastIo = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            HttpRequest.Builder rb = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT);
            if (apiKey != null) rb.header("Authorization", "Bearer " + apiKey);
            if (workspaceId != null) rb.header("x-workspace-id", workspaceId);

            HttpRequest.BodyPublisher pub = HttpRequest.BodyPublishers.noBody();
            if (bodyBytes != null) {
                pub = HttpRequest.BodyPublishers.ofByteArray(bodyBytes);
                rb.header("Content-Type", "application/json");
            }
            rb.method(method, pub);

            HttpResponse<byte[]> resp;
            try {
                resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
            } catch (java.net.http.HttpConnectTimeoutException | java.net.ConnectException ex) {
                lastIo = ex;
                if (attempt >= maxRetries) throw ex;
                sleepBackoff(attempt);
                continue;
            } catch (IOException ex) {
                throw ex;
            }

            int status = resp.statusCode();
            byte[] respBody = resp.body();

            if (isRetryableStatus(status) && attempt < maxRetries) {
                long hintMs = parseRetryAfterMs(resp.headers().firstValue("retry-after").orElse(null));
                sleepBackoff(attempt, hintMs);
                continue;
            }

            if (status >= 400) {
                String raw = respBody == null ? "" : new String(respBody, StandardCharsets.UTF_8);
                throw new ApiException(status, extractMessage(raw), raw);
            }
            if (status == 204 || responseType == Void.class || respBody == null || respBody.length == 0) {
                return null;
            }
            return mapper.readValue(respBody, responseType);
        }
        if (lastIo != null) throw lastIo;
        throw new IOException("ledgermem: request failed after retries");
    }

    private static boolean isRetryableStatus(int status) {
        // 501 Not Implemented is a permanent failure — retrying wastes round-trips.
        if (status == 501) return false;
        return status == 429 || (status >= 500 && status < 600);
    }

    private static void sleepBackoff(int attempt) throws InterruptedException {
        sleepBackoff(attempt, -1L);
    }

    private static void sleepBackoff(int attempt, long hintMs) throws InterruptedException {
        long delay;
        if (hintMs >= 0) {
            delay = Math.min(hintMs, RETRY_MAX_DELAY_MS);
        } else {
            long shifted = RETRY_BASE_DELAY_MS << Math.min(attempt, 20);
            long capped = Math.min(shifted, RETRY_MAX_DELAY_MS);
            delay = ThreadLocalRandom.current().nextLong(0, capped + 1);
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            // Restore interrupt status so callers can detect it deterministically.
            Thread.currentThread().interrupt();
            throw ie;
        }
    }

    /** Parse Retry-After (delta-seconds or HTTP-date). Returns -1 if absent or unparseable. */
    private static long parseRetryAfterMs(String value) {
        if (value == null || value.isEmpty()) return -1L;
        String trimmed = value.trim();
        try {
            long secs = Long.parseLong(trimmed);
            return Math.max(0L, secs * 1000L);
        } catch (NumberFormatException ignored) {
            // fall through to HTTP-date
        }
        try {
            java.time.ZonedDateTime when = java.time.ZonedDateTime.parse(
                    trimmed, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME);
            long delta = java.time.Duration.between(java.time.ZonedDateTime.now(when.getZone()), when).toMillis();
            return Math.max(0L, delta);
        } catch (java.time.format.DateTimeParseException ignored) {
            return -1L;
        }
    }

    private String extractMessage(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            Map<?, ?> probe = mapper.readValue(raw, Map.class);
            Object m = probe.get("message");
            if (m == null) m = probe.get("error");
            return m == null ? "" : m.toString();
        } catch (IOException e) {
            return "";
        }
    }

    private static String buildQuery(Map<String, String> query) {
        if (query == null || query.isEmpty()) return "";
        return "?" + query.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    private static String first(String a, String b) {
        return a != null && !a.isEmpty() ? a : b;
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /** Mutable builder. */
    public static final class Builder {
        private String apiKey;
        private String workspaceId;
        private String baseUrl;
        private HttpClient httpClient;
        private Duration timeout = Duration.ofSeconds(10);
        private int maxRetries = DEFAULT_MAX_RETRIES;

        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder workspaceId(String workspaceId) { this.workspaceId = workspaceId; return this; }
        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder httpClient(HttpClient httpClient) { this.httpClient = httpClient; return this; }
        public Builder connectTimeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout);
            return this;
        }
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public LedgerMemClient build() {
            return new LedgerMemClient(this);
        }
    }

    /** Helper to build a query string map preserving insertion order. */
    static Map<String, String> queryMap() {
        return new LinkedHashMap<>();
    }
}
