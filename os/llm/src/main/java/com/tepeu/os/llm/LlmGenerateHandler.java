package com.tepeu.os.llm;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.session.LedgerEntry;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEvent;
import com.tepeu.os.session.SessionStore;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallHandler;
import com.tepeu.os.syscall.SyscallResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * llm.generate — args 只带 config；messages 恒 derive(surface)。
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
        Session session = sessions.get(ctx.sessionId()).orElse(null);
        if (session == null) {
            return SyscallResult.failure("NOT_FOUND", "session not found");
        }
        List<SessionEvent> surface = session.logReplace().surface();
        SyscallResult replay = verifyPrevious(session, surface);
        if (replay != null) {
            return replay;
        }
        PreparedRequest prepared = LlmTransport.prepare(surface, family, model, system);
        LlmTransport.Reply reply = transport.complete(prepared);
        session.ledger().record(NAME, reply.usage(), attrs(prepared, model, system));
        return SyscallResult.success(reply.output(), reply.usage(), 0L);
    }

    /** 失配返回失败结果；无需复核返回 null。 */
    private static SyscallResult verifyPrevious(Session session, List<SessionEvent> surface) {
        LedgerEntry last = lastLlm(session);
        if (last == null) {
            return null;
        }
        Map<String, String> attrs = last.attrs();
        long throughSeq = Long.parseLong(attrs.getOrDefault("throughSeq", "0"));
        List<SessionEvent> prefix = new ArrayList<>();
        for (SessionEvent event : surface) {
            if (event.seq() <= throughSeq) {
                prefix.add(event);
            }
        }
        ProtocolFamily family = ProtocolFamily.parse(attrs.get("family"));
        PreparedRequest replay = LlmTransport.prepare(
                prefix,
                family,
                attrs.getOrDefault("model", ""),
                attrs.getOrDefault("system", ""));
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

    private static Map<String, String> attrs(PreparedRequest prepared, String model, String system) {
        Map<String, String> attrs = new LinkedHashMap<>();
        attrs.put("digest", prepared.digest());
        attrs.put("deriveVersion", prepared.deriveVersion());
        attrs.put("normalizeVersion", prepared.normalizeVersion());
        attrs.put("projectVersion", prepared.projectVersion());
        attrs.put("family", prepared.family().name().toLowerCase());
        attrs.put("model", model);
        attrs.put("system", system);
        attrs.put("throughSeq", Long.toString(prepared.throughSeq()));
        return attrs;
    }
}
