package com.tepeu.service.chat;

import com.tepeu.agent.tool.FileTools;
import com.tepeu.agent.tool.ShellTools;
import com.tepeu.agent.tool.ToolEventEmitter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChatService}.
 *
 * <p>Covers the {@link ChatService#testConnection(String)} connection probe (Phase-2 acceptance
 * criterion 1: "fill API key → connection test succeeds") without touching the network — the
 * {@link ChatModelFactory} is mocked, and a {@link StubChatModel} stands in for the real provider
 * model. The probe must return {@code true} when a round-trip completes and {@code false} for any
 * resolution or call failure (bad key, 401, network).
 *
 * <p>Also includes a smoke test for {@link ChatService#streamWithTools} that asserts the
 * {@code ChatClient} assembly path (single tool-callback registration) does not throw and still
 * streams the model's response — the tool loop itself is only exercised against a real LLM.
 */
class ChatServiceTest {

    private ChatModelFactory factory;
    private FileTools fileTools;
    private ChatService service;

    @BeforeEach
    void setUp() {
        factory = mock(ChatModelFactory.class);
        // Real @Tool bean so ToolCallbacks.from() discovers the tool methods；smoke 不真正写盘
        fileTools = FileTools.forTests(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "tepeu-chat-test"));
        ShellTools shellTools = ShellTools.forTests(
                java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "tepeu-chat-test"));
        service = new ChatService(factory, fileTools, shellTools, new tools.jackson.databind.ObjectMapper());
    }

    // --- testConnection ------------------------------------------------------------------

    @Test
    void testConnection_validModel_returnsTrue() {
        when(factory.getChatModel("openai")).thenReturn(new StubChatModel("ok"));

        assertTrue(service.testConnection("openai"));
    }

    @Test
    void testConnection_missingApiKey_returnsFalse() {
        // ChatModelFactory surfaces a missing/blank key as IllegalStateException(MISSING_API_KEY).
        when(factory.getChatModel("openai"))
                .thenThrow(new IllegalStateException("MISSING_API_KEY"));

        assertFalse(service.testConnection("openai"));
    }

    @Test
    void testConnection_modelCallThrows_returnsFalse() {
        // Model resolves, but the actual call fails (e.g. 401 Unauthorized / network). The probe
        // must swallow it and report failure rather than propagating.
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("401 Unauthorized"));
        when(factory.getChatModel("openai")).thenReturn(model);

        assertFalse(service.testConnection("openai"));
    }

    @Test
    void testConnection_unknownProvider_returnsFalse() {
        when(factory.getChatModel("deepseek"))
                .thenThrow(new IllegalArgumentException("UNKNOWN_PROVIDER"));

        assertFalse(service.testConnection("deepseek"));
    }

    // --- streamWithTools assembly smoke test ---------------------------------------------

    @Test
    void streamWithTools_stubModel_emitsResponseWithoutThrowing() {
        // Regression guard for the tool-callback registration fix: the ChatClient must be assembled
        // with the wrapped callbacks exactly once and still stream the model's reply. The stub does
        // not request tool calls, so the advisor passes the response through.
        when(factory.getChatModel("openai")).thenReturn(new StubChatModel("hello"));

        Flux<ChatResponse> flux = service.streamWithTools(
                "openai", new Prompt(new org.springframework.ai.chat.messages.UserMessage("hi")),
                ToolEventEmitter.NOOP);

        List<ChatResponse> emitted = flux.collectList().block();
        assertNotNull(emitted, "streamWithTools must produce a flux, not throw at assembly");
        assertFalse(emitted.isEmpty(), "the stub's reply must stream through");
    }
}
