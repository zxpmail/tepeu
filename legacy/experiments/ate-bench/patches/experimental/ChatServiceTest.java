package com.tepeu.service.chat;

import com.tepeu.agent.tool.FileTools;
import com.tepeu.agent.tool.ShellTools;
import com.tepeu.agent.tool.ToolEventEmitter;
import com.tepeu.agent.tool.ToolRegistry;
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

/** Unit tests for experimental {@link ChatService}（经 ToolRegistry 注入工具）。 */
class ChatServiceTest {

    private ChatModelFactory factory;
    private ChatService service;

    @BeforeEach
    void setUp() {
        factory = mock(ChatModelFactory.class);
        FileTools fileTools = FileTools.forTests(
                java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "tepeu-chat-test"));
        ShellTools shellTools = ShellTools.forTests(
                java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "tepeu-chat-test"));
        ToolRegistry registry = new ToolRegistry()
                .register("fileTools", fileTools)
                .register("shellTools", shellTools);
        service = new ChatService(factory, registry, new tools.jackson.databind.ObjectMapper());
    }

    @Test
    void testConnection_validModel_returnsTrue() {
        when(factory.getChatModel("openai")).thenReturn(new StubChatModel("ok"));
        assertTrue(service.testConnection("openai"));
    }

    @Test
    void testConnection_missingApiKey_returnsFalse() {
        when(factory.getChatModel("openai"))
                .thenThrow(new IllegalStateException("MISSING_API_KEY"));
        assertFalse(service.testConnection("openai"));
    }

    @Test
    void testConnection_modelCallThrows_returnsFalse() {
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

    @Test
    void streamWithTools_stubModel_emitsResponseWithoutThrowing() {
        when(factory.getChatModel("openai")).thenReturn(new StubChatModel("hello"));
        Flux<ChatResponse> flux = service.streamWithTools(
                "openai", new Prompt(new org.springframework.ai.chat.messages.UserMessage("hi")),
                ToolEventEmitter.NOOP);
        List<ChatResponse> emitted = flux.collectList().block();
        assertNotNull(emitted, "streamWithTools must produce a flux, not throw at assembly");
        assertFalse(emitted.isEmpty(), "the stub's reply must stream through");
    }
}
