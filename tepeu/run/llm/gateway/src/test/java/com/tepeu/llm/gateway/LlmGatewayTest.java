package com.tepeu.llm.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tepeu.session.SessionEvent;
import com.tepeu.session.SessionEventType;
import com.tepeu.session.SessionLog;
import com.tepeu.syscall.SyscallResult;
import com.tepeu.syscall.Usage;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LlmGatewayTest {

    @Test
    void visibleKeepsDialogueInLogOrder() {
        List<SessionEvent> events = List.of(
                event(1, SessionEventType.USER_MESSAGE, "hi"),
                event(2, SessionEventType.TOOL_CALL, "fs.read"),
                event(3, SessionEventType.REASONING, "thinking"),
                event(4, SessionEventType.ASSISTANT_MESSAGE, "hello"),
                event(5, SessionEventType.PLAN_STEP, "step"),
                event(6, SessionEventType.TOOL_RESULT, "bytes"),
                event(7, SessionEventType.USER_MESSAGE, "again"));
        assertEquals(List.of(
                new LlmMessage(Role.USER, "hi"),
                new LlmMessage(Role.ASSISTANT, "hello"),
                new LlmMessage(Role.USER, "again")),
                LlmGateway.visible(events));
    }

    @Test
    void gatewayPassesDerivedMessagesAndReturnsVerbatim() {
        SessionLog log = logOf(
                event(1, SessionEventType.USER_MESSAGE, "hi"),
                event(2, SessionEventType.ASSISTANT_MESSAGE, "hello"));
        AtomicReference<List<LlmMessage>> seen = new AtomicReference<>();
        SyscallResult back = SyscallResult.success("ok", new Usage(2, 3));
        LlmGateway gateway = new LlmGateway(visible -> {
            seen.set(visible);
            return back;
        });
        SyscallResult r = gateway.generate(log);
        assertEquals(back, r);
        assertEquals(List.of(
                new LlmMessage(Role.USER, "hi"),
                new LlmMessage(Role.ASSISTANT, "hello")),
                seen.get());
    }

    @Test
    void emptyLogStillAsksBackend() {
        LlmGateway gateway = new LlmGateway(visible -> {
            assertEquals(List.of(), visible);
            return SyscallResult.success("ok");
        });
        assertTrue(gateway.generate(logOf()).ok());
    }

    private static SessionEvent event(long seq, SessionEventType type, String body) {
        return new SessionEvent(seq, type, Instant.EPOCH, body, Map.of());
    }

    private static SessionLog logOf(SessionEvent... events) {
        return new SessionLog() {
            @Override
            public long append(SessionEventType type, String body, Map<String, String> attrs) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<SessionEvent> readAll() {
                return List.of(events);
            }

            @Override
            public Optional<SessionEvent> get(long seq) {
                return Optional.empty();
            }
        };
    }
}
