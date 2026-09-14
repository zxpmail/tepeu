package com.tepeu.llm.anthropic;

import com.tepeu.llm.gateway.LlmBackend;
import com.tepeu.llm.gateway.LlmMessage;
import com.tepeu.llm.gateway.Role;
import com.tepeu.syscall.SyscallResult;
import com.tepeu.syscall.Usage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Anthropic Messages API 后端。SYSTEM 消息进 {@code system} 字段，
 * USER/TOOL 归 {@code user}、ASSISTANT 归 {@code assistant}，同角色相邻合并。
 * 失败不抛：非 2xx 合成 {@code LLM_HTTP_<n>}，网络异常合成 {@code LLM_IO_ERROR}。
 */
public final class AnthropicBackend implements LlmBackend {

    public static final int MAX_TOKENS = 4096;
    public static final Duration TIMEOUT = Duration.ofSeconds(60);
    static final String API_VERSION = "2023-06-01";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final HttpClient http;
    private final String token;
    private final String baseUrl;
    private final String model;

    public AnthropicBackend(String token, String baseUrl, String model) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token blank");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl blank");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("model blank");
        }
        this.token = token;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;
        this.http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    }

    @Override
    public SyscallResult generate(List<LlmMessage> visible) {
        Objects.requireNonNull(visible, "visible");
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/v1/messages"))
                    .timeout(TIMEOUT)
                    .header("x-api-key", token)
                    .header("Authorization", "Bearer " + token)
                    .header("anthropic-version", API_VERSION)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            buildRequest(visible, model, MAX_TOKENS)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return SyscallResult.failure("LLM_HTTP_" + response.statusCode(),
                        "HTTP " + response.statusCode());
            }
            Parsed parsed = parseResponse(response.body());
            return SyscallResult.success(parsed.text(), parsed.usage());
        } catch (IOException e) {
            return SyscallResult.failure("LLM_IO_ERROR", "io error");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SyscallResult.failure("LLM_IO_ERROR", "interrupted");
        }
    }

    /** 可见序列 → Messages API 请求体。纯函数。 */
    public static String buildRequest(List<LlmMessage> visible, String model, int maxTokens) {
        List<String> system = new ArrayList<>();
        List<Map<String, Object>> messages = new ArrayList<>();
        for (LlmMessage m : visible) {
            if (m.role() == Role.SYSTEM) {
                system.add(m.body());
                continue;
            }
            String role = m.role() == Role.ASSISTANT ? "assistant" : "user";
            if (!messages.isEmpty() && role.equals(messages.getLast().get("role"))) {
                messages.getLast().put("content", messages.getLast().get("content") + "\n\n" + m.body());
            } else {
                messages.add(new LinkedHashMap<>(Map.of("role", role, "content", m.body())));
            }
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("model", model);
        root.put("max_tokens", maxTokens);
        if (!system.isEmpty()) {
            root.put("system", String.join("\n\n", system));
        }
        root.put("messages", messages);
        return MAPPER.writeValueAsString(root);
    }

    /** Messages API 响应体 → 文本与用量。text 块相连，其余块忽略。纯函数。 */
    public static Parsed parseResponse(String json) {
        JsonNode root = MAPPER.readTree(json);
        StringBuilder text = new StringBuilder();
        JsonNode content = root.path("content");
        for (int i = 0; i < content.size(); i++) {
            JsonNode block = content.get(i);
            if ("text".equals(block.path("type").asString())) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(block.path("text").asString());
            }
        }
        JsonNode usage = root.path("usage");
        return new Parsed(text.toString(),
                new Usage(usage.path("input_tokens").asLong(0), usage.path("output_tokens").asLong(0)));
    }

    /** 解析结果：答复文本 + 透传用量。 */
    public record Parsed(String text, Usage usage) {
    }
}
