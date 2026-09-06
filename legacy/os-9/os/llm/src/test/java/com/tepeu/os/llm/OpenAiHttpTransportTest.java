package com.tepeu.os.llm;

import com.tepeu.os.llm.local.LlmGenerateHandler;
import com.tepeu.os.llm.local.OpenAiHttpTransport;
import com.tepeu.os.llm.local.OpenAiProjector;
import com.tepeu.os.llm.local.CanonicalJson;
import com.tepeu.os.llm.local.LlmTransports;
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

class OpenAiHttpTransportTest {

    @Test
    void postsPreparedWireAndParsesTextUsage() {
        Session session = newSession();
        session.log().append(SessionEventType.USER_MESSAGE, "hi", Map.of());
        PreparedRequest prepared = LlmTransports.prepare(
                session.logReplace().surface(), ProtocolFamily.OPENAI, "gpt-test", "sys");
        assertEquals(OpenAiProjector.VERSION, prepared.projectVersion());
        assertFalse(prepared.wireJson().contains("cache_control"), prepared.wireJson());
        assertTrue(prepared.wireJson().contains("\"role\":\"system\""), prepared.wireJson());

        AtomicReference<String> posted = new AtomicReference<>();
        AtomicReference<Map<String, String>> headers = new AtomicReference<>();
        OpenAiHttpTransport transport = new OpenAiHttpTransport(
                "https://api.openai.com", "test-key", (url, h, json) -> {
                    assertEquals("https://api.openai.com/v1/chat/completions", url);
                    posted.set(json);
                    headers.set(Map.copyOf(h));
                    return new HttpRoundTrip.Exchange(200, """
                            {"choices":[{"message":{"role":"assistant","content":"hello world"}}],\
                            "usage":{"prompt_tokens":10,"completion_tokens":2,\
                            "prompt_tokens_details":{"cached_tokens":3}}}
                            """);
                });
        LlmTransport.Reply reply = transport.complete(prepared);
        assertEquals(prepared.wireJson(), posted.get());
        assertEquals("Bearer test-key", headers.get().get("authorization"));
        assertEquals("application/json", headers.get().get("content-type"));
        assertEquals("hello world", reply.output());
        assertEquals(7, reply.usage().inputTokens());
        assertEquals(2, reply.usage().outputTokens());
        assertEquals(3, reply.usage().cacheReadTokens());
        assertEquals(0, reply.usage().cacheWriteTokens());
        assertTrue(reply.usage().cost().isEmpty());
    }

    @Test
    void concatenatesArrayContentParts() {
        OpenAiHttpTransport transport = new OpenAiHttpTransport(
                "https://api.openai.com", "k", (url, h, json) ->
                        new HttpRoundTrip.Exchange(200, """
                                {"choices":[{"message":{"content":[\
                                {"type":"text","text":"a"},{"type":"text","text":"b"}]}}]}
                                """));
        Session session = newSession();
        PreparedRequest prepared = LlmTransports.prepare(
                session.logReplace().surface(), ProtocolFamily.OPENAI, "m", "");
        assertEquals("ab", transport.complete(prepared).output());
    }

    @Test
    void httpErrorIsTransportAndHandlerDoesNotLedger() {
        SessionStore store = new InMemorySessionStore();
        Session session = store.create(
                Principal.personal(new PrincipalId("u")),
                Namespace.ofWorkspace(new WorkspaceId("w")),
                Optional.empty());
        session.log().append(SessionEventType.USER_MESSAGE, "q", Map.of());
        OpenAiHttpTransport transport = new OpenAiHttpTransport(
                "https://api.openai.com", "k", (url, h, json) ->
                        new HttpRoundTrip.Exchange(401, "{\"error\":\"nope\"}"));
        LlmGenerateHandler handler = new LlmGenerateHandler(store, transport);
        TurnContext ctx = new TurnContext(session.owner(), session.namespace(), session.id(), Optional.empty());
        SyscallResult r = handler.handle(ctx, new Syscall(
                LlmGenerateHandler.NAME, Map.of("model", "m", "family", "openai")));
        assertFalse(r.ok());
        assertEquals("TRANSPORT", r.errorCode().orElse(""));
        assertTrue(session.ledger().readAll().isEmpty());
    }

    @Test
    void rejectsAnthropicFamily() {
        Session session = newSession();
        PreparedRequest prepared = LlmTransports.prepare(
                session.logReplace().surface(), ProtocolFamily.ANTHROPIC, "claude", "");
        OpenAiHttpTransport transport = new OpenAiHttpTransport(
                "https://api.openai.com", "k", (url, h, json) -> {
                    throw new AssertionError("must not POST");
                });
        LlmTransportException e = assertThrows(LlmTransportException.class, () -> transport.complete(prepared));
        assertEquals("CONFIG", e.errorCode());
    }

    @Test
    void canonicalJsonRoundTripOpenaiWire() {
        Session session = newSession();
        session.log().append(SessionEventType.USER_MESSAGE, "hi", Map.of());
        PreparedRequest prepared = LlmTransports.prepare(
                session.logReplace().surface(), ProtocolFamily.OPENAI, "m", "sys");
        Object parsed = CanonicalJson.read(prepared.wireJson());
        assertEquals(prepared.wireJson(), CanonicalJson.write(parsed));
        assertEquals("1", prepared.projectVersion());
    }

    @Test
    void blankApiKeyRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new OpenAiHttpTransport("https://api.openai.com", "  ", (u, h, j) -> {
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
