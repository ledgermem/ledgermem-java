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
import java.util.stream.Collectors;

/** Official Java client for the LedgerMem API. */
public final class LedgerMemClient {

    private static final String DEFAULT_BASE_URL = "https://api.proofly.dev";
    private static final String USER_AGENT = "ledgermem-java/0.1.0";

    private final String apiKey;
    private final String workspaceId;
    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final MemoriesService memories;

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
        HttpRequest.Builder rb = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT);
        if (apiKey != null) rb.header("Authorization", "Bearer " + apiKey);
        if (workspaceId != null) rb.header("x-workspace-id", workspaceId);

        HttpRequest.BodyPublisher pub = HttpRequest.BodyPublishers.noBody();
        if (body != null) {
            byte[] bytes = mapper.writeValueAsBytes(body);
            pub = HttpRequest.BodyPublishers.ofByteArray(bytes);
            rb.header("Content-Type", "application/json");
        }
        rb.method(method, pub);

        HttpResponse<byte[]> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
        int status = resp.statusCode();
        byte[] respBody = resp.body();

        if (status >= 400) {
            String raw = respBody == null ? "" : new String(respBody, StandardCharsets.UTF_8);
            throw new ApiException(status, extractMessage(raw), raw);
        }
        if (status == 204 || responseType == Void.class || respBody == null || respBody.length == 0) {
            return null;
        }
        return mapper.readValue(respBody, responseType);
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

        public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
        public Builder workspaceId(String workspaceId) { this.workspaceId = workspaceId; return this; }
        public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
        public Builder httpClient(HttpClient httpClient) { this.httpClient = httpClient; return this; }
        public Builder connectTimeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout);
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
