package com.tepeu.os.llm;

import com.tepeu.os.syscall.Usage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * OpenAI Chat Completions HTTP 薄壳。body = prepared.wireJson。
 * 投影 v1 无 cache_control。Anthropic 族拒绝。
 */
public final class OpenAiHttpTransport implements LlmTransport {

    public static final String DEFAULT_BASE_URL = "https://api.openai.com";

    private final String baseUrl;
    private final String apiKey;
    private final HttpRoundTrip http;

    public OpenAiHttpTransport(String baseUrl, String apiKey, HttpRoundTrip http) {
        this.baseUrl = stripSlash(Objects.requireNonNull(baseUrl, "baseUrl"));
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        if (this.apiKey.isBlank()) {
            throw new IllegalArgumentException("apiKey blank");
        }
        this.http = Objects.requireNonNull(http, "http");
    }

    public static OpenAiHttpTransport jdk(String baseUrl, String apiKey) {
        return new OpenAiHttpTransport(baseUrl, apiKey, new JdkHttpRoundTrip());
    }

    /** 读 {@code OPENAI_API_KEY}；可选 {@code OPENAI_BASE_URL}。 */
    public static OpenAiHttpTransport fromEnv() {
        String key = System.getenv("OPENAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY missing");
        }
        String base = System.getenv("OPENAI_BASE_URL");
        if (base == null || base.isBlank()) {
            base = DEFAULT_BASE_URL;
        }
        return jdk(base, key);
    }

    @Override
    public Reply complete(PreparedRequest prepared) {
        Objects.requireNonNull(prepared, "prepared");
        if (prepared.family() != ProtocolFamily.OPENAI) {
            throw new LlmTransportException("CONFIG",
                    "OpenAiHttpTransport cannot send family=" + prepared.family());
        }
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("content-type", "application/json");
        headers.put("authorization", "Bearer " + apiKey);
        HttpRoundTrip.Exchange exchange = http.post(baseUrl + "/v1/chat/completions", headers, prepared.wireJson());
        if (exchange.status() < 200 || exchange.status() >= 300) {
            throw new LlmTransportException("TRANSPORT",
                    "openai http " + exchange.status() + ": " + truncate(exchange.body()));
        }
        return parse(exchange.body());
    }

    static Reply parse(String body) {
        Object root;
        try {
            root = CanonicalJson.read(body);
        } catch (RuntimeException e) {
            throw new LlmTransportException("TRANSPORT", "openai response not JSON: " + e.getMessage(), e);
        }
        if (!(root instanceof Map<?, ?> map)) {
            throw new LlmTransportException("TRANSPORT", "openai response not object");
        }
        return new Reply(text(map.get("choices")), usage(map.get("usage")));
    }

    private static String text(Object choicesRaw) {
        if (!(choicesRaw instanceof List<?> choices) || choices.isEmpty()) {
            return "";
        }
        Object first = choices.get(0);
        if (!(first instanceof Map<?, ?> choice)) {
            return "";
        }
        Object message = choice.get("message");
        if (!(message instanceof Map<?, ?> msg)) {
            return "";
        }
        Object content = msg.get("content");
        if (content instanceof String s) {
            return s;
        }
        if (content instanceof List<?> parts) {
            StringBuilder text = new StringBuilder();
            for (Object part : parts) {
                if (part instanceof Map<?, ?> p && "text".equals(String.valueOf(p.get("type")))) {
                    Object t = p.get("text");
                    if (t != null) {
                        text.append(t);
                    }
                }
            }
            return text.toString();
        }
        return "";
    }

    /**
     * OpenAI {@code prompt_tokens} 含缓存；Usage.inputTokens 是非缓存输入。
     * cacheWrite 无对等字段，记 0。
     */
    private static Usage usage(Object raw) {
        if (!(raw instanceof Map<?, ?> u)) {
            return new Usage(0, 0, 0, 0);
        }
        long prompt = longVal(u.get("prompt_tokens"));
        long cached = 0L;
        Object details = u.get("prompt_tokens_details");
        if (details instanceof Map<?, ?> d) {
            cached = longVal(d.get("cached_tokens"));
        }
        if (cached < 0L) {
            cached = 0L;
        }
        long input = prompt - cached;
        if (input < 0L) {
            input = 0L;
        }
        return new Usage(
                input,
                longVal(u.get("completion_tokens")),
                cached,
                0L,
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
