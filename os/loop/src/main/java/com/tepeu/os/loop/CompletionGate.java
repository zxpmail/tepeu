package com.tepeu.os.loop;

import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEvent;
import com.tepeu.os.session.SessionEventType;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 完成证据门：无证据不得 completed。SSE / Todo / 回到 idle 不是完成。
 */
public final class CompletionGate {

    private CompletionGate() {
    }

    public static boolean allow(Session session, CompletionClaim claim, long afterSeq) {
        return refuseReason(session, claim, afterSeq).isEmpty();
    }

    public static Optional<String> refuseReason(Session session, CompletionClaim claim, long afterSeq) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(claim, "claim");
        List<SessionEvent> slice = session.log().readAll().stream()
                .filter(e -> e.seq() > afterSeq)
                .toList();
        return switch (claim) {
            case REPLY -> {
                boolean ok = slice.stream().anyMatch(e ->
                        e.type() == SessionEventType.ASSISTANT_MESSAGE && !e.body().isBlank());
                yield ok ? Optional.empty() : Optional.of("reply requires non-blank ASSISTANT_MESSAGE");
            }
            case TOOL_PAIR -> toolPairReason(slice);
        };
    }

    private static Optional<String> toolPairReason(List<SessionEvent> slice) {
        int open = 0;
        boolean anyCall = false;
        for (SessionEvent event : slice) {
            if (event.type() == SessionEventType.TOOL_CALL) {
                open++;
                anyCall = true;
            } else if (event.type() == SessionEventType.TOOL_RESULT) {
                if (open == 0) {
                    return Optional.of("TOOL_RESULT without TOOL_CALL");
                }
                open--;
            }
        }
        if (!anyCall) {
            return Optional.of("tool claim requires TOOL_CALL");
        }
        if (open != 0) {
            return Optional.of("TOOL_CALL/TOOL_RESULT unpaired");
        }
        return Optional.empty();
    }
}
