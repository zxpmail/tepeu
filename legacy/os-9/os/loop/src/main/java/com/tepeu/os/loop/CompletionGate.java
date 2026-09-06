package com.tepeu.os.loop;

import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEvent;
import com.tepeu.os.session.SessionEventType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 完成证据门：无证据不得 completed。SSE / Todo / 回到 idle 不是完成。
 * 控制循环可并行；宣布结束只许本门读 entries。工具回报、模型声称、投影都不是终态。
 */
public final class CompletionGate {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

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
            case PLAN -> {
                boolean ok = slice.stream().anyMatch(e -> e.type() == SessionEventType.PLAN_STEP);
                yield ok ? Optional.empty() : Optional.of("plan claim requires PLAN_STEP");
            }
            case FILE -> fileReason(session, slice);
        };
    }

    /**
     * 主路推断：永远要 REPLY；有工具则 TOOL_PAIR；有 PLAN_STEP 则 PLAN；有 locator 则 FILE。
     */
    public static List<CompletionClaim> infer(Session session, long afterSeq) {
        Objects.requireNonNull(session, "session");
        List<SessionEvent> slice = session.log().readAll().stream()
                .filter(e -> e.seq() > afterSeq)
                .toList();
        List<CompletionClaim> claims = new ArrayList<>();
        claims.add(CompletionClaim.REPLY);
        if (slice.stream().anyMatch(e -> e.type() == SessionEventType.TOOL_CALL)) {
            claims.add(CompletionClaim.TOOL_PAIR);
        }
        if (slice.stream().anyMatch(e -> e.type() == SessionEventType.PLAN_STEP)) {
            claims.add(CompletionClaim.PLAN);
        }
        if (slice.stream().anyMatch(e ->
                e.type() == SessionEventType.TOOL_RESULT && e.attrs().containsKey("locator"))) {
            claims.add(CompletionClaim.FILE);
        }
        return List.copyOf(claims);
    }

    private static Optional<String> fileReason(Session session, List<SessionEvent> slice) {
        boolean any = false;
        for (SessionEvent event : slice) {
            if (event.type() != SessionEventType.TOOL_RESULT) {
                continue;
            }
            String locator = event.attrs().get("locator");
            if (locator == null || locator.isBlank()) {
                continue;
            }
            any = true;
            if (!SHA256.matcher(locator).matches()) {
                return Optional.of("FILE locator must be sha256 hex");
            }
            if (session.blobs().get(locator).isEmpty()) {
                return Optional.of("FILE locator missing from ContentStore");
            }
        }
        if (!any) {
            return Optional.of("file claim requires locator");
        }
        return Optional.empty();
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
