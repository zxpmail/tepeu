package com.tepeu.os.llm;

import com.tepeu.os.syscall.Usage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Anthropic Messages HTTP 薄壳。body = prepared.wireJson（含 max_tokens）。
 * 投影已带 cache_control，故带 prompt-caching beta 头。OpenAI 族拒绝（走 {@link OpenAiHttpTransport}）。
 */
public final class AnthropicHttpTransport implements LlmTransport {

    public static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    public static final String ANTHROPIC_VERSION = "2023-06-01";
    public static final String PROMPT_CACHING_BETA = "prompt-caching-2024-07-31";

    private final String baseUrl;
    private final String apiKey;
    private final HttpRoundTrip http;

    public AnthropicHttpTransport(String baseUrl, String apiKey, HttpRoundTrip http) {
        this.baseUrl = stripSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        if (this.apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey blank");
        }
        this.http = Objects.requireNonNull(http, "http");
    }

    public static AnthropicHttpTransport jdk(String baseUrl, String apiKey) {
        return new AnthropicHttpTransport(baseUrl, apiKey, new JdkHttpRoundTrip());
    }

    /** 读 {@code ANTHROPIC_API_KEY}；可选 {@code ANTHROPIC_BASE_URL}。 */
    public static AnthropicHttpTransport fromEnv() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY missing");
        }
        String base = System.getenv("ANTHROPIC_BASE_URL");
        if (base == null || base.isBlank()) {
            base = DEFAULT_BASE_URL;
        }
        return jdk(base, key);
    }

    @Override
    public Reply complete(PreparedRequest prepared) {
        Objects.requireNonNull(prepared, "prepared");
        if (prepared.family() != ProtocolFamily.ANTHROPIC) {
            throw new LlmTransportException("CONFIG",
                    "AnthropicHttpTransport cannot send family=" + prepared.family());
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("content-type", "application/json");
        headers.put("x-api-key", apiKey);
        headers.put("anthropic-version", ANTHROPIC_VERSION);
        headers.put("anthropic-beta", PROMPT_CACHING_BETA);
        HttpRoundTrip.Exchange exchange = http.post(baseUrl + "/v1/messages", headers, prepared.wireJson());
        if (exchange.status() < 200 || exchange.status() >= 300) {
            throw new LlmTransportException("TRANSPORT",
                    "anthropic http " + exchange.status() + ": " + truncate(exchange.body()));
        }
        return parse(exchange.body());
    }

    static Reply parse(String body) {
        Object root;
        try {
            root = CanonicalJson.read(body);
        } catch (RuntimeException e) {
            throw new LlmTransportException("TRANSPORT", "anthropic response not JSON: " + e.getMessage(), e);
        }
        if (!(root instanceof Map<?, ?> map)) {
            throw new LlmTransportException("TRANSPORT", "anthropic response not object");
        }
        StringBuilder text = new StringBuilder();
        Object content = map.get("content");
        if (content instanceof List<?> blocks) {
            for (Object block : blocks) {
                if (block instanceof Map<?, ?> b && "text".equals(String.valueOf(b.get("type")))) {
                    Object t = b.get("text");
                    if (t != null) {
                        text.append(t);
                    }
                }
            }
        }
        return new Reply(text.toString(), usage(map.get("usage")));
    }

    private static Usage usage(Object raw) {
        if (!(raw instanceof Map<?, ?> u)) {
            return new Usage(0, 0, 0, 0);
        }
        return new Usage(
                longVal(u.get("input_tokens")),
                longVal(u.get("output_tokens")),
                longVal(u.get("cache_read_input_tokens")),
                longVal(u.get("cache_creation_input_tokens")),
                Optional.empty());
    }

    private static long longVal(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        return 0L;
    }

    private static String stripSlash(String base) {
        if (base.endsWith("/")) {
            return base.substring(0, base.length() - 1);
        }
        return base;
    }

    private static String truncate(String body) {
        if (body.length() <= 240) {
            return body;
        }
        return body.substring(0, 240);
    }
}
