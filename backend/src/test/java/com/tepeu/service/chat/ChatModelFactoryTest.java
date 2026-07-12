package com.tepeu.service.chat;

import com.tepeu.model.LlmProvider;
import com.tepeu.service.LlmProviderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TDD tests for {@link ChatModelFactory}.
 *
 * <p>Scope: factory <b>selection + validation + model-name wiring</b> logic only. The actual SDK
 * client construction (OpenAiSetup / AnthropicSetup / OllamaApi) needs a network + key and is
 * therefore covered by {@code mvn compile} (signature correctness), not by these unit tests. The
 * factory exposes {@code protected} build hooks so this test can capture the model name each
 * provider branch would set without touching the network.
 */
class ChatModelFactoryTest {

    private LlmProviderService providerService;

    @BeforeEach
    void setUp() {
        providerService = mock(LlmProviderService.class);
    }

    // ---- validation / error paths --------------------------------------------------------

    @Test
    void getChatModel_unknownProvider_throws() {
        when(providerService.getProvider("deepseek")).thenReturn(Optional.empty());
        ChatModelFactory factory = new ChatModelFactory(providerService);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> factory.getChatModel("deepseek"));
        assertEquals("UNKNOWN_PROVIDER", ex.getMessage());
    }

    @Test
    void getChatModel_disabledProvider_throws() {
        when(providerService.getProvider("openai"))
                .thenReturn(Optional.of(provider("openai", "sk-xxx", true, "gpt-4o", false)));
        ChatModelFactory factory = new ChatModelFactory(providerService);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> factory.getChatModel("openai"));
        assertEquals("PROVIDER_DISABLED", ex.getMessage());
    }

    @Test
    void getChatModel_blankApiKey_throws() {
        when(providerService.getProvider("anthropic"))
                .thenReturn(Optional.of(provider("anthropic", "", true, "claude-sonnet-4-20250514", true)));
        ChatModelFactory factory = new ChatModelFactory(providerService);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> factory.getChatModel("anthropic"));
        assertEquals("MISSING_API_KEY", ex.getMessage());
    }

    @Test
    void getChatModel_blankModel_throws() {
        when(providerService.getProvider("ollama"))
                .thenReturn(Optional.of(provider("ollama", "ollama-no-key-needed", "http://localhost:11434", "  ", true)));
        ChatModelFactory factory = new ChatModelFactory(providerService);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> factory.getChatModel("ollama"));
        assertEquals("MISSING_MODEL", ex.getMessage());
    }

    // ---- selection + model-name wiring ---------------------------------------------------

    @Test
    void getChatModel_openai_dispatchesToOpenAiBuilderWithModelName() {
        when(providerService.getProvider("openai"))
                .thenReturn(Optional.of(provider("openai", "sk-test", "https://api.openai.com", "gpt-4o", true)));

        CapturingFactory factory = new CapturingFactory(providerService);
        ChatModel model = factory.getChatModel("openai");

        assertEquals("openai", factory.lastBranch);
        assertEquals("gpt-4o", factory.lastModelName);
        // The stubbed ChatModel is returned to the caller.
        assertEquals("stub-openai", extractModelName(model));
    }

    @Test
    void getChatModel_anthropic_dispatchesToAnthropicBuilderWithModelName() {
        when(providerService.getProvider("anthropic"))
                .thenReturn(Optional.of(provider("anthropic", "sk-ant-test", null, "claude-sonnet-4-20250514", true)));

        CapturingFactory factory = new CapturingFactory(providerService);
        factory.getChatModel("anthropic");

        assertEquals("anthropic", factory.lastBranch);
        assertEquals("claude-sonnet-4-20250514", factory.lastModelName);
    }

    @Test
    void getChatModel_ollama_dispatchesToOllamaBuilderWithModelName() {
        when(providerService.getProvider("ollama"))
                .thenReturn(Optional.of(provider("ollama", "ignored", "http://localhost:11434", "llama3", true)));

        CapturingFactory factory = new CapturingFactory(providerService);
        factory.getChatModel("ollama");

        assertEquals("ollama", factory.lastBranch);
        assertEquals("llama3", factory.lastModelName);
    }

    @Test
    void getChatModel_unknownProviderIdValue_throws() {
        // A row exists but its providerId is not one of the three supported values.
        when(providerService.getProvider("bedrock"))
                .thenReturn(Optional.of(provider("bedrock", "k", true, "m", true)));
        ChatModelFactory factory = new ChatModelFactory(providerService);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> factory.getChatModel("bedrock"));
        assertEquals("UNSUPPORTED_PROVIDER", ex.getMessage());
    }

    @Test
    void normalizeOpenAiBaseUrl_appendsV1ForHostRoot() {
        assertEquals("https://api.deepseek.com/v1",
                ChatModelFactory.normalizeOpenAiBaseUrl("https://api.deepseek.com"));
        assertEquals("https://api.deepseek.com/v1",
                ChatModelFactory.normalizeOpenAiBaseUrl("https://api.deepseek.com/"));
        assertEquals("https://api.deepseek.com/v1",
                ChatModelFactory.normalizeOpenAiBaseUrl("https://api.deepseek.com/v1"));
        assertEquals("https://api.deepseek.com/anthropic",
                ChatModelFactory.normalizeOpenAiBaseUrl("https://api.deepseek.com/anthropic"));
    }

    // ---- helpers -------------------------------------------------------------------------

    private static LlmProvider provider(String providerId, String apiKey, String baseUrl,
                                        String defaultModel, boolean enabled) {
        LlmProvider p = new LlmProvider();
        p.setProviderId(providerId);
        p.setApiKey(apiKey);
        p.setBaseUrl(baseUrl);
        p.setDefaultModel(defaultModel);
        p.setEnabled(enabled);
        return p;
    }

    /** Variant with explicit baseUrl (overload for ollama/blank-model test). */
    private static LlmProvider provider(String providerId, String apiKey, boolean hasBaseUrl,
                                        String defaultModel, boolean enabled) {
        return provider(providerId, apiKey,
                hasBaseUrl ? "https://example.com" : null, defaultModel, enabled);
    }

    private static String extractModelName(ChatModel model) {
        ChatOptions opts = model.getDefaultOptions();
        // Our stub returns a ChatOptions whose model() is the stub tag.
        return opts == null ? null : opts.getModel();
    }

    /**
     * Subclass that overrides the SDK-construction hooks so no network/SDK client is built. It
     * records which branch ran and the model name the factory resolved, then returns a tiny stub
     * ChatModel so {@link ChatService} can be wired later without a live provider.
     */
    static class CapturingFactory extends ChatModelFactory {
        String lastBranch;
        String lastModelName;

        CapturingFactory(LlmProviderService providerService) {
            super(providerService);
        }

        @Override
        protected ChatModel buildOpenAi(String apiKey, String baseUrl, String modelName) {
            lastBranch = "openai";
            lastModelName = modelName;
            return stubModel("stub-openai");
        }

        @Override
        protected ChatModel buildAnthropic(String apiKey, String baseUrl, String modelName) {
            lastBranch = "anthropic";
            lastModelName = modelName;
            return stubModel("stub-anthropic");
        }

        @Override
        protected ChatModel buildOllama(String baseUrl, String modelName) {
            lastBranch = "ollama";
            lastModelName = modelName;
            return stubModel("stub-ollama");
        }

        private static ChatModel stubModel(String tag) {
            return new StubChatModel(tag);
        }
    }
}
