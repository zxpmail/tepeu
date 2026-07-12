package com.tepeu.controller;

import com.tepeu.agent.AgentOrchestrator;
import com.tepeu.dto.ChatRequest;
import com.tepeu.model.Session;
import com.tepeu.service.SessionService;
import com.tepeu.service.TaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Standalone unit tests for {@link ChatController} — no Spring context, no real LLM.
 */
class ChatControllerTest {

    private AgentOrchestrator orchestrator;
    private SessionService sessionService;
    private TaskService taskService;
    private ChatController controller;

    @BeforeEach
    void setUp() {
        orchestrator = mock(AgentOrchestrator.class);
        sessionService = mock(SessionService.class);
        taskService = mock(TaskService.class);
        controller = new ChatController(orchestrator, sessionService, taskService, new ObjectMapper());
    }

    @Test
    void mapError_missingApiKey_mapsToStableCodeAndSafeMessage() {
        String[] mapped = ChatController.mapError(new IllegalStateException("MISSING_API_KEY"));
        assertEquals("MISSING_API_KEY", mapped[0]);
        assertNotEquals("MISSING_API_KEY", mapped[1], "message must be human text, not the raw code");
        assertFalse(mapped[1].isEmpty());
    }

    @Test
    void mapError_allKnownCodesAreMapped() {
        for (String code : new String[]{
                "UNKNOWN_PROVIDER", "UNSUPPORTED_PROVIDER", "PROVIDER_DISABLED",
                "MISSING_API_KEY", "MISSING_MODEL"}) {
            String[] mapped = ChatController.mapError(new IllegalStateException(code));
            assertEquals(code, mapped[0], "code " + code + " should pass through");
            assertNotNull(mapped[1]);
        }
    }

    @Test
    void mapError_authFailure_mapsToSafeAuthFailedWithoutLeakingKey() {
        String[] mapped = ChatController.mapError(
                new RuntimeException("com.openai.AuthenticationException: 401 Invalid API key sk-leaked"));
        assertEquals("AUTH_FAILED", mapped[0]);
        assertEquals("API key invalid or unauthorized", mapped[1]);
        assertFalse(mapped[1].contains("sk-leaked"));
    }

    @Test
    void mapError_forbidden_mapsToSafeForbidden() {
        String[] mapped = ChatController.mapError(
                new RuntimeException("PermissionDeniedException: 403 Request not allowed"));
        assertEquals("FORBIDDEN", mapped[0]);
        assertEquals("Provider refused the request (403)", mapped[1]);
    }

    @Test
    void mapError_unclassifiedExceptionCollapsesToGenericChatError() {
        String[] mapped = ChatController.mapError(new RuntimeException("weird internal boom"));
        assertEquals("CHAT_ERROR", mapped[0]);
        assertEquals("Chat request failed", mapped[1], "internal detail must never leak");
    }

    @Test
    void mapError_nullThrowableCollapsesToGeneric() {
        String[] mapped = ChatController.mapError(null);
        assertEquals("CHAT_ERROR", mapped[0]);
    }

    @Test
    void mapError_nullMessageCollapsesToGeneric() {
        String[] mapped = ChatController.mapError(new IllegalStateException());
        assertEquals("CHAT_ERROR", mapped[0]);
    }

    @Test
    void stream_blankMessage_doesNotInvokeOrchestrator() {
        ChatRequest req = chatReq("  ", "ws-1", null, "openai");

        SseEmitter emitter = controller.stream(req);

        assertNotNull(emitter);
        verifyNoInteractions(orchestrator);
        verify(sessionService, never()).appendMessage(anyString(), anyString(), anyString());
    }

    @Test
    void stream_nullProvider_doesNotInvokeOrchestrator() {
        ChatRequest req = chatReq("hello", "ws-1", null, null);

        controller.stream(req);

        verifyNoInteractions(orchestrator);
    }

    @Test
    void stream_missingApiKeyFluxError_completesWithoutThrowing() {
        ChatRequest req = chatReq("hello", "ws-1", "sess-1", "openai");
        Session session = new Session();
        session.setId("sess-1");
        session.setWorkspaceId("ws-1");
        when(sessionService.getSession("sess-1")).thenReturn(java.util.Optional.of(session));
        when(sessionService.listMessages("sess-1")).thenReturn(java.util.List.of());
        when(orchestrator.streamTurn(eq("openai"), any(), any(), isNull(), any(), any()))
                .thenReturn(Flux.error(new IllegalStateException("MISSING_API_KEY")));

        SseEmitter emitter = controller.stream(req);

        assertNotNull(emitter);
        verify(sessionService).appendMessage("sess-1", "user", "hello");
        verify(orchestrator).streamTurn(eq("openai"), any(), any(), isNull(), any(), any());
        verify(sessionService, never()).appendMessage(eq("sess-1"), eq("assistant"), anyString());
    }

    @Test
    void stream_unknownSession_emitsNotFoundAndSkipsOrchestrator() {
        ChatRequest req = chatReq("hello", "ws-1", "ghost", "openai");
        when(sessionService.getSession("ghost")).thenReturn(java.util.Optional.empty());

        controller.stream(req);

        verifyNoInteractions(orchestrator);
        verify(sessionService, never()).appendMessage(anyString(), anyString(), anyString());
    }

    @Test
    void stream_createsSessionWhenSessionIdAbsent() {
        ChatRequest req = chatReq("hello there", "ws-1", null, "openai");
        Session created = new Session();
        created.setId("sess-new");
        created.setWorkspaceId("ws-1");
        when(sessionService.createSession(eq("ws-1"), anyString())).thenReturn(created);
        when(sessionService.listMessages("sess-new")).thenReturn(java.util.List.of());
        when(orchestrator.streamTurn(eq("openai"), any(), any(), isNull(), any(), any())).thenReturn(Flux.empty());

        controller.stream(req);

        verify(sessionService).createSession(eq("ws-1"), argThat(t -> t != null && !t.isBlank()));
        verify(sessionService).appendMessage("sess-new", "user", "hello there");
    }

    @Test
    void errorPayload_serializesToSpecSchema() throws Exception {
        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("type", "error");
        payload.put("code", "MISSING_API_KEY");
        payload.put("message", "Provider API key is not configured");
        String json = new ObjectMapper().writeValueAsString(payload);

        assertTrue(json.contains("\"type\":\"error\""), json);
        assertTrue(json.contains("\"code\":\"MISSING_API_KEY\""), json);
        assertTrue(json.contains("\"message\":\"Provider API key is not configured\""), json);
    }

    private static ChatRequest chatReq(String message, String workspaceId, String sessionId, String provider) {
        ChatRequest req = new ChatRequest();
        req.setMessage(message);
        req.setWorkspaceId(workspaceId);
        req.setSessionId(sessionId);
        req.setProvider(provider);
        return req;
    }
}
