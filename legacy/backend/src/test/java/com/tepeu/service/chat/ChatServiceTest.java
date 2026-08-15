package com.tepeu.service.chat;

import com.tepeu.agent.hook.ApprovalStore;
import com.tepeu.agent.hook.DangerousToolHook;
import com.tepeu.agent.mcp.McpToolBridge;
import com.tepeu.agent.tool.ListDirTool;
import com.tepeu.agent.tool.ReadFileTool;
import com.tepeu.agent.tool.RunCommandTool;
import com.tepeu.agent.tool.ToolEventEmitter;
import com.tepeu.agent.tool.ToolRegistry;
import com.tepeu.agent.tool.WriteFileTool;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        ListDirTool listDirTool = ListDirTool.forTests(
                java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "tepeu-chat-test"));
        ReadFileTool readFileTool = ReadFileTool.forTests(
                java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "tepeu-chat-test"));
        WriteFileTool writeFileTool = WriteFileTool.forTests(
                java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "tepeu-chat-test"));
        RunCommandTool runCommandTool = RunCommandTool.forTests(
                java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "tepeu-chat-test"));
        ToolRegistry registry = new ToolRegistry()
                .register("listDir", listDirTool)
                .register("readFile", readFileTool)
                .register("writeFile", writeFileTool)
                .register("runCommand", runCommandTool);
        @SuppressWarnings("unchecked")
        ObjectProvider<java.util.List<io.modelcontextprotocol.client.McpSyncClient>> clients =
                mock(ObjectProvider.class);
        when(clients.getIfAvailable(any())).thenReturn(java.util.List.of());
        McpToolBridge mcpBridge = new McpToolBridge(clients, false, 0L);
        service = new ChatService(
                factory,
                registry,
                mcpBridge,
                new tools.jackson.databind.ObjectMapper(),
                new DangerousToolHook(),
                new ApprovalStore(0L));
    }

    @Test
    void testConnection_validModel_returnsNull() {
        when(factory.getChatModel("openai")).thenReturn(new StubChatModel("ok"));
        assertNull(service.testConnection("openai"));
    }

    @Test
    void testConnection_missingApiKey_returnsCode() {
        when(factory.getChatModel("openai"))
                .thenThrow(new IllegalStateException("MISSING_API_KEY"));
        assertEquals("MISSING_API_KEY", service.testConnection("openai"));
    }

    @Test
    void testConnection_modelCallThrows_returnsFailed() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("401 Unauthorized"));
        when(factory.getChatModel("openai")).thenReturn(model);
        assertEquals("CONNECTION_FAILED", service.testConnection("openai"));
    }

    @Test
    void testConnection_unknownProvider_returnsCode() {
        when(factory.getChatModel("missing-provider"))
                .thenThrow(new IllegalArgumentException("UNKNOWN_PROVIDER"));
        assertEquals("UNKNOWN_PROVIDER", service.testConnection("missing-provider"));
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
