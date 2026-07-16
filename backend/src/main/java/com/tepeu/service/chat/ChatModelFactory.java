package com.tepeu.service.chat;

import com.tepeu.model.LlmProvider;
import com.tepeu.service.LlmProviderService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Builds a Spring AI 2.0 {@link ChatModel} for a given provider from values stored in the
 * {@code llm_provider} SQLite table (AES-256-GCM encrypted key, decrypted upstream by
 * {@link LlmProviderService#getProvider(String)}).
 *
 * <h3>Autoconfig vs programmatic decision</h3>
 * We build provider {@code ChatModel}s <b>programmatically per call</b> instead of relying on the
 * Spring AI starter auto-configuration. Rationale: the starters
 * ({@code spring-ai-starter-model-openai/anthropic/ollama}) auto-configure a single
 * {@code ChatModel} bean per provider from {@code spring.ai.*.api-key} properties, but in Tepeu
 * those properties are intentionally {@code ""} — real keys live per-row in the DB and may differ
 * across users/workspaces. There is no way to feed DB-sourced, row-varying credentials into the
 * auto-configured beans, so we construct each model explicitly here from DB values.
 *
 * <p>Per-call construction cost: each call builds one OkHttp-backed SDK client
 * ({@code OpenAIClient}/{@code AnthropicClient}) plus the {@code ChatModel} wrapper. That is cheap
 * relative to the model round-trip and is acceptable at Phase-1 scale (low QPS, single user). No
 * caching yet — revisit in T2+ if profiling demands it. Do not pre-emptively cache: keys can be
 * rotated via the provider admin endpoint, and a stale cached client would keep using the old key.
 *
 * <h3>Spring AI 2.0.0 API used (verified via {@code javap} on the 2.0.0 jars)</h3>
 * <ul>
 *   <li><b>OpenAI</b>: {@code OpenAiSetup.setupSyncClient(baseUrl, apiKey, credential,
 *       azureDeploymentName, azureServiceVersion, organizationId, isMicrosoftFoundry,
 *       isGitHubModels, modelName, timeout, maxRetries, proxy, customHeaders,
 *       observationRegistry, meterRegistry, customizers)} &rarr; {@code OpenAIClient}, then
 *       {@code OpenAiChatModel.builder().openAiClient(client).options(opts).build()}.
 *       In 2.0 the legacy {@code OpenAiApi} class is gone; the official {@code openai-java} SDK
 *       client is now the transport.</li>
 *   <li><b>Anthropic</b>: {@code AnthropicSetup.setupSyncClient(baseUrl, apiKey, timeout,
 *       maxConnections, proxy, customHeaders)} &rarr; {@code AnthropicClient}, then
 *       {@code AnthropicChatModel.builder().anthropicClient(client).options(opts).build()}.</li>
 *   <li><b>Ollama</b>: {@code OllamaApi.builder().baseUrl(url).build()} then
 *       {@code OllamaChatModel.builder().ollamaApi(api).options(opts).build()}.</li>
 * </ul>
 * The {@code model(String)} option setter comes from {@code ChatOptions.Builder} (shared super).
 *
 * <h3>Testability</h3> The three {@code protected buildXxx(...)} hooks are overridden by
 * {@code ChatModelFactoryTest} so the selection/validation logic is unit-tested without a network.
 */
@Component
public class ChatModelFactory {

    /** HTTP call budget for the SDK clients. Conservative default; not yet tunable per provider. */
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    /** OpenAI client max HTTP retries (Spring AI default in the autoconfig is 10). */
    static final int OPENAI_MAX_RETRIES = 1;

    private final LlmProviderService providerService;

    public ChatModelFactory(LlmProviderService providerService) {
        this.providerService = providerService;
    }

    /**
     * Resolve and build a ready-to-use {@link ChatModel} for {@code providerId}.
     *
     * @throws IllegalArgumentException for {@code UNKNOWN_PROVIDER} (no DB row) or
     *         {@code UNSUPPORTED_PROVIDER} (row exists but providerId is not openai/anthropic/ollama).
     * @throws IllegalStateException    for {@code PROVIDER_DISABLED}, {@code MISSING_API_KEY}, or
     *         {@code MISSING_MODEL}. The exception {@code getMessage()} carries the stable error
     *         code (no free text) so the controller layer can map it cleanly.
     */
    public ChatModel getChatModel(String providerId) {
        LlmProvider provider = providerService.getProvider(providerId)
                .orElseThrow(() -> new IllegalArgumentException("UNKNOWN_PROVIDER"));

        if (!provider.isEnabled()) {
            throw new IllegalStateException("PROVIDER_DISABLED");
        }
        String apiKey = provider.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("MISSING_API_KEY");
        }
        if (apiKey.trim().toLowerCase().startsWith("http://")
                || apiKey.trim().toLowerCase().startsWith("https://")) {
            throw new IllegalStateException("API_KEY_LOOKS_LIKE_URL");
        }
        String modelName = provider.getDefaultModel();
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalStateException("MISSING_MODEL");
        }
        String baseUrl = provider.getBaseUrl();

        return switch (provider.getProviderId()) {
            case "openai"    -> buildOpenAi(apiKey, baseUrl, modelName);
            case "anthropic" -> buildAnthropic(apiKey, baseUrl, modelName);
            case "ollama"    -> buildOllama(baseUrl, modelName);
            default -> throw new IllegalArgumentException("UNSUPPORTED_PROVIDER");
        };
    }

    /**
     * Build an OpenAI {@link ChatModel}. {@code credential} is null for standard API-key auth
     * (the {@code apiKey} string is used); the Credential slot is only for Azure/bearer flows,
     * which Tepeu does not use in Phase 2.
     *
     * <p>Arg order of {@code OpenAiSetup.setupSyncClient}/{@code setupAsyncClient} (16 params,
     * verified via javap LocalVariableTable): {@code (baseUrl, apiKey, credential,
     * azureDeploymentName, azureServiceVersion, organizationId, isMicrosoftFoundry,
     * isGitHubModels, modelName, timeout, maxRetries, proxy, customHeaders, observationRegistry,
     * meterRegistry, httpClientBuilderCustomizers)}. Non-applicable slots are
     * {@code null}/{@code false}/empty.
     *
     * <p><b>Must wire both sync and async clients.</b> Spring AI 2.0 {@code OpenAiChatModel.Builder}
     * falls back to {@code setupAsyncClient} from {@link org.springframework.ai.openai.OpenAiChatOptions}
     * when async is omitted — our options only set {@code model}, so that fallback builds a client
     * with no API key and fails with {@code At least one credential source must be specified}.
     * Streaming uses the async client; connection tests use sync.
     */
    protected ChatModel buildOpenAi(String apiKey, String baseUrl, String modelName) {
        // openai-java 的 PRODUCTION_URL 带 /v1；兼容用户填 https://api.deepseek.com 的情况
        String effectiveBaseUrl = normalizeOpenAiBaseUrl(baseUrl);
        var client = org.springframework.ai.openai.setup.OpenAiSetup.setupSyncClient(
                effectiveBaseUrl,                          // baseUrl
                apiKey,                                    // apiKey (standard key auth)
                null,                                      // credential (Azure/bearer only)
                null,                                      // azureDeploymentName
                null,                                      // azureOpenAiServiceVersion
                null,                                      // organizationId
                false,                                     // isMicrosoftFoundry
                false,                                     // isGitHubModels
                modelName,                                 // modelName (used for UA / header)
                DEFAULT_TIMEOUT,                           // timeout
                OPENAI_MAX_RETRIES,                        // maxRetries
                null,                                      // proxy
                Map.of(),                                  // customHeaders
                io.micrometer.observation.ObservationRegistry.NOOP,
                null,                                      // meterRegistry
                java.util.List.of()                        // httpClientBuilderCustomizers
        );
        // 流式聊天走 async；必须用同一套凭证，不能依赖 options 回落
        var asyncClient = org.springframework.ai.openai.setup.OpenAiSetup.setupAsyncClient(
                effectiveBaseUrl,
                apiKey,
                null,
                null,
                null,
                null,
                false,
                false,
                modelName,
                DEFAULT_TIMEOUT,
                OPENAI_MAX_RETRIES,
                null,
                Map.of(),
                io.micrometer.observation.ObservationRegistry.NOOP,
                null,
                java.util.List.of()
        );
        var options = org.springframework.ai.openai.OpenAiChatOptions.builder()
                .model(modelName)
                .build();
        return org.springframework.ai.openai.OpenAiChatModel.builder()
                .openAiClient(client)
                .openAiClientAsync(asyncClient)
                .options(options)
                .build();
    }

    /**
     * Build an Anthropic {@link ChatModel}. Uses the 6-arg sync/async setup overloads
     * (no observation/meter/executor wiring) — verified via javap:
     * {@code setupSyncClient/setupAsyncClient(baseUrl, apiKey, timeout, maxConnections, proxy, customHeaders)}.
     *
     * <p>同 OpenAI：流式依赖 async client，必须显式传入带密钥与 baseUrl 的 async，
     * 否则会落到官方 {@code api.anthropic.com} 且无有效凭证。
     */
    protected ChatModel buildAnthropic(String apiKey, String baseUrl, String modelName) {
        // Anthropic's public cloud endpoint is https://api.anthropic.com; a blank baseUrl in the
        // DB means "use the SDK default", so pass null through (setupSyncClient treats null as the
        // default base URL).
        String effectiveBaseUrl = (baseUrl == null || baseUrl.isBlank()) ? null : baseUrl;
        var client = org.springframework.ai.anthropic.AnthropicSetup.setupSyncClient(
                effectiveBaseUrl,                          // baseUrl (null → SDK default)
                apiKey,                                    // apiKey
                DEFAULT_TIMEOUT,                           // timeout
                null,                                      // maxConnections (null → SDK default)
                null,                                      // proxy
                Map.of()                                   // customHeaders
        );
        var asyncClient = org.springframework.ai.anthropic.AnthropicSetup.setupAsyncClient(
                effectiveBaseUrl,
                apiKey,
                DEFAULT_TIMEOUT,
                null,
                null,
                Map.of()
        );
        var options = org.springframework.ai.anthropic.AnthropicChatOptions.builder()
                .model(modelName)
                .build();
        return org.springframework.ai.anthropic.AnthropicChatModel.builder()
                .anthropicClient(client)
                .anthropicClientAsync(asyncClient)
                .options(options)
                .build();
    }

    /**
     * openai-java 期望 baseUrl 已含 {@code /v1}（官方默认 {@code https://api.openai.com/v1}）。
     * DeepSeek 文档常写 {@code https://api.deepseek.com}；这里补上 {@code /v1} 避免路径错误。
     */
    static String normalizeOpenAiBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return baseUrl;
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/v1")) {
            return trimmed;
        }
        // 仅对「主机根路径」补 /v1；已带其它 path（如 /openai）的不擅自改写
        int scheme = trimmed.indexOf("://");
        String afterScheme = scheme >= 0 ? trimmed.substring(scheme + 3) : trimmed;
        if (!afterScheme.contains("/")) {
            return trimmed + "/v1";
        }
        return trimmed;
    }

    /**
     * Build an Ollama {@link ChatModel}. Ollama runs locally and needs no API key; the
     * {@code baseUrl} (default {@code http://localhost:11434}) is the only endpoint config.
     */
    protected ChatModel buildOllama(String baseUrl, String modelName) {
        var apiBuilder = org.springframework.ai.ollama.api.OllamaApi.builder();
        if (baseUrl != null && !baseUrl.isBlank()) {
            apiBuilder.baseUrl(baseUrl);
        }
        var api = apiBuilder.build();
        var options = org.springframework.ai.ollama.api.OllamaChatOptions.builder()
                .model(modelName)
                .build();
        return org.springframework.ai.ollama.OllamaChatModel.builder()
                .ollamaApi(api)
                .options(options)
                .build();
    }

    /** Convenience: resolve without throwing — returns empty for any resolution failure. */
    public Optional<ChatModel> tryGetChatModel(String providerId) {
        try {
            return Optional.of(getChatModel(providerId));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
