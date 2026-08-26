package com.tepeu.os.llm;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.session.LedgerEntry;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEvent;
import com.tepeu.os.session.SessionStore;
import com.tepeu.os.session.SurfaceEpoch;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallHandler;
import com.tepeu.os.syscall.SyscallResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * llm.generate — args 只带 config；messages 恒 {@code Observation.view(surface)}。
 * 上笔 digest 失配则失败可见、不发传输。
 */
public final class LlmGenerateHandler implements SyscallHandler {

    public static final String NAME = "llm.generate";

    private final SessionStore sessions;
    private final LlmTransport transport;

    public LlmGenerateHandler(SessionStore sessions, LlmTransport transport) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    @Override
    public SyscallResult handle(TurnContext ctx, Syscall syscall) {
        if (syscall.args().containsKey("messages")) {
            return SyscallResult.failure("STRUCTURAL", "llm.* does not accept messages; derive from log");
        }
        String model = syscall.args().getOrDefault("model", "").trim();
        if (model.isEmpty()) {
            return SyscallResult.failure("CONFIG", "model required");
        }
        ProtocolFamily family;
        try {
            family = ProtocolFamily.parse(syscall.args().get("family"));
        } catch (IllegalArgumentException e) {
            return SyscallResult.failure("CONFIG", e.getMessage());
        }
        String system = syscall.args().getOrDefault("system", "");
        int maxTokens;
        try {
            maxTokens = parseMaxTokens(syscall.args().get("max_tokens"));
        } catch (IllegalArgumentException e) {
            return SyscallResult.failure("CONFIG", e.getMessage());
        }
        Session session = sessions.get(ctx.sessionId()).orElse(null);
        if (session == null) {
            return SyscallResult.failure("NOT_FOUND", "session not found");
        }
        List<SessionEvent> surface = session.logReplace().surface();
        SyscallResult replay = verifyPrevious(session, surface);
        if (replay != null) {
            return replay;
        }
        PreparedRequest prepared = LlmTransport.prepare(surface, family, model, system, maxTokens);
        LlmTransport.Reply reply;
        long started = System.nanoTime();
        try {
            reply = transport.complete(prepared);
        } catch (LlmTransportException e) {
            return SyscallResult.failure(e.errorCode(), e.getMessage());
        }
        long latencyMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
        session.ledger().record(NAME, reply.usage(), attrs(session, prepared, model, system, maxTokens));
        return SyscallResult.success(reply.output(), reply.usage(), latencyMs);
    }

    /** 失配返回失败结果；无需复核返回 null。 */
    private static SyscallResult verifyPrevious(Session session, List<SessionEvent> surface) {
        LedgerEntry last = lastLlm(session);
        if (last == null) {
            return null;
        }
        Map<String, String> attrs = last.attrs();
        String recordedEpoch = attrs.getOrDefault("surfaceEpoch", "0");
        String currentEpoch = SurfaceEpoch.current(session.registers());
        if (!recordedEpoch.equals(currentEpoch)) {
            return null;
        }
        long throughSeq = Long.parseLong(attrs.getOrDefault("throughSeq", "0"));
        List<SessionEvent> prefix = new ArrayList<>();
        for (SessionEvent event : surface) {
            if (event.seq() <= throughSeq) {
                prefix.add(event);
            }
        }
        ProtocolFamily family = ProtocolFamily.parse(attrs.get("family"));
        int maxTokens;
        try {
            maxTokens = parseMaxTokens(attrs.get("maxTokens"));
        } catch (IllegalArgumentException e) {
            return SyscallResult.failure("ASSERTION", "previous llm maxTokens: " + e.getMessage());
        }
        PreparedRequest replay = LlmTransport.prepare(
                prefix,
                family,
                attrs.getOrDefault("model", ""),
                attrs.getOrDefault("system", ""),
                maxTokens);
        if (!replay.digest().equals(attrs.get("digest"))) {
            return SyscallResult.failure("ASSERTION", "previous llm prepare digest mismatch");
        }
        return null;
    }

    private static LedgerEntry lastLlm(Session session) {
        List<LedgerEntry> all = session.ledger().readAll();
        for (int i = all.size() - 1; i >= 0; i--) {
            LedgerEntry entry = all.get(i);
            if (NAME.equals(entry.syscallName()) && entry.attrs().containsKey("digest")) {
                return entry;
            }
        }
        return null;
    }

    private static Map<String, String> attrs(
            Session session, PreparedRequest prepared, String model, String system, int maxTokens) {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("digest", prepared.digest());
        attrs.put("deriveVersion", prepared.deriveVersion());
        attrs.put("normalizeVersion", prepared.normalizeVersion());
        attrs.put("projectVersion", prepared.projectVersion());
        attrs.put("family", prepared.family().name().toLowerCase());
        attrs.put("model", model);
        attrs.put("system", system);
        attrs.put("maxTokens", Integer.toString(maxTokens));
        attrs.put("throughSeq", Long.toString(prepared.throughSeq()));
        attrs.put("surfaceEpoch", SurfaceEpoch.current(session.registers()));
        return attrs;
    }

    private static int parseMaxTokens(String raw) {
        if (raw == null || raw.isBlank()) {
            return AnthropicProjector.DEFAULT_MAX_TOKENS;
        }
        try {
            int n = Integer.parseInt(raw.trim());
            if (n < 1) {
                throw new IllegalArgumentException("max_tokens < 1");
            }
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("max_tokens not an integer");
        }
    }
}
