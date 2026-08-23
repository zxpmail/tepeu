package com.tepeu.os.llm;

import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.session.SessionStore;
import com.tepeu.os.session.memory.InMemorySessionStore;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnthropicHttpTransportTest {

    @Test
    void postsPreparedWireAndParsesTextUsage() {
        Session session = newSession();
        session.log().append(SessionEventType.USER_MESSAGE, "hi", Map.of());
        PreparedRequest prepared = LlmTransport.prepare(
                session.logReplace().surface(), ProtocolFamily.ANTHROPIC, "claude-test", "");
        assertTrue(prepared.wireJson().contains("\"max_tokens\":1024"), prepared.wireJson());
        assertEquals(AnthropicProjector.VERSION, prepared.projectVersion());

        AtomicReference<String> posted = new AtomicReference<>();
        AtomicReference<Map<String, String>> headers = new AtomicReference<>();
        AnthropicHttpTransport transport = new AnthropicHttpTransport(
                "https://api.anthropic.com", "test-key", (url, h, json) -> {
                    assertEquals("https://api.anthropic.com/v1/messages", url);
                    posted.set(json);
                    headers.set(Map.copyOf(h));
                    return new HttpRoundTrip.Exchange(200, """
                            {"content":[{"type":"text","text":"hello "},{"type":"text","text":"world"}],\
                            "usage":{"input_tokens":3,"output_tokens":2,\
                            "cache_read_input_tokens":1,"cache_creation_input_tokens":4}}
                            """);
                });
        LlmTransport.Reply reply = transport.complete(prepared);
        assertEquals(prepared.wireJson(), posted.get());
        assertEquals("test-key", headers.get().get("x-api-key"));
        assertEquals(AnthropicHttpTransport.ANTHROPIC_VERSION, headers.get().get("anthropic-version"));
        assertEquals(AnthropicHttpTransport.PROMPT_CACHING_BETA, headers.get().get("anthropic-beta"));
        assertEquals("hello world", reply.output());
        assertEquals(3, reply.usage().inputTokens());
        assertEquals(2, reply.usage().outputTokens());
        assertEquals(1, reply.usage().cacheReadTokens());
        assertEquals(4, reply.usage().cacheWriteTokens());
        assertTrue(reply.usage().cost().isEmpty());
    }

    @Test
    void httpErrorIsTransportAndHandlerDoesNotLedger() {
        SessionStore store = new InMemorySessionStore();
        Session session = store.create(
                Principal.personal(new PrincipalId("u")),
                Namespace.ofWorkspace(new WorkspaceId("w")),
                Optional.empty());
        session.log().append(SessionEventType.USER_MESSAGE, "q", Map.of());
        AnthropicHttpTransport transport = new AnthropicHttpTransport(
                "https://api.anthropic.com", "k", (url, h, json) ->
                        new HttpRoundTrip.Exchange(401, "{\"error\":\"nope\"}"));
        LlmGenerateHandler handler = new LlmGenerateHandler(store, transport);
        TurnContext ctx = new TurnContext(session.owner(), session.namespace(), session.id(), Optional.empty());
        SyscallResult r = handler.handle(ctx, new Syscall(LlmGenerateHandler.NAME, Map.of("model", "m")));
        assertFalse(r.ok());
        assertEquals("TRANSPORT", r.errorCode().orElse(""));
        assertTrue(session.ledger().readAll().isEmpty());
    }

    @Test
    void rejectsOpenaiFamily() {
        Session session = newSession();
        PreparedRequest prepared = LlmTransport.prepare(
                session.logReplace().surface(), ProtocolFamily.OPENAI, "gpt", "");
        AnthropicHttpTransport transport = new AnthropicHttpTransport(
                "https://api.anthropic.com", "k", (url, h, json) -> {
                    throw new AssertionError("must not POST");
                });
        LlmTransportException e = assertThrows(LlmTransportException.class, () -> transport.complete(prepared));
        assertEquals("CONFIG", e.errorCode());
    }

    @Test
    void canonicalJsonRoundTripAnthropicWire() {
        Session session = newSession();
        session.log().append(SessionEventType.USER_MESSAGE, "hi", Map.of());
        PreparedRequest prepared = LlmTransport.prepare(
                session.logReplace().surface(), ProtocolFamily.ANTHROPIC, "m", "sys", 32);
        Object parsed = CanonicalJson.read(prepared.wireJson());
        assertEquals(prepared.wireJson(), CanonicalJson.write(parsed));
        assertEquals("2", prepared.projectVersion());
    }

    @Test
    void blankApiKeyRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new AnthropicHttpTransport("https://api.anthropic.com", "  ", (u, h, j) -> {
                    throw new AssertionError();
                }));
    }

    private static Session newSession() {
        return new InMemorySessionStore().create(
                Principal.personal(new PrincipalId("u")),
                Namespace.ofWorkspace(new WorkspaceId("w")),
                Optional.empty());
    }
}
