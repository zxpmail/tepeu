package com.tepeu.os.llm;

import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.session.memory.InMemorySessionStore;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveHttpTransportTest {

    @Test
    void fromEnvMatchesProcessKeys() {
        boolean hasKey = notBlank(System.getenv("ANTHROPIC_API_KEY"))
                || notBlank(System.getenv("OPENAI_API_KEY"));
        assertTrue(hasKey == LlmTransports.fromEnv().isPresent());
    }

    @EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
    @Test
    void anthropicLiveRoundTrip() {
        InMemorySessionStore store = new InMemorySessionStore();
        Session session = newSession(store);
        session.log().append(SessionEventType.USER_MESSAGE, "Reply with the single word pong.", Map.of());
        String model = envOr("ANTHROPIC_MODEL", "claude-3-5-haiku-latest");
        LlmGenerateHandler handler = new LlmGenerateHandler(store, AnthropicHttpTransport.fromEnv());
        SyscallResult result = handler.handle(
                new TurnContext(
                        session.owner(), session.namespace(), session.id(), Optional.empty()),
                new Syscall(LlmGenerateHandler.NAME, Map.of(
                        "model", model, "family", "anthropic", "max_tokens", "32")));
        assertTrue(result.ok(), result.output());
        assertFalse(result.output().isBlank());
        assertTrue(result.usage().orElseThrow().cost().isEmpty(), "cost stays n/a");
    }

    @EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
    @Test
    void openaiLiveRoundTrip() {
        InMemorySessionStore store = new InMemorySessionStore();
        Session session = newSession(store);
        session.log().append(SessionEventType.USER_MESSAGE, "Reply with the single word pong.", Map.of());
        String model = envOr("OPENAI_MODEL", "gpt-4o-mini");
        LlmGenerateHandler handler = new LlmGenerateHandler(store, OpenAiHttpTransport.fromEnv());
        SyscallResult result = handler.handle(
                new TurnContext(
                        session.owner(), session.namespace(), session.id(), Optional.empty()),
                new Syscall(LlmGenerateHandler.NAME, Map.of(
                        "model", model, "family", "openai", "max_tokens", "32")));
        assertTrue(result.ok(), result.output());
        assertFalse(result.output().isBlank());
        assertTrue(result.usage().orElseThrow().cost().isEmpty(), "cost stays n/a");
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Session newSession(InMemorySessionStore store) {
        return store.create(
                Principal.personal(new PrincipalId("live-u")),
                Namespace.ofWorkspace(new WorkspaceId("live-w")),
                Optional.empty());
    }
}
