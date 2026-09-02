package com.tepeu.os.loop.local;

import com.tepeu.os.loop.*;

import com.tepeu.os.bus.CapabilityBus;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEvent;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallResult;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 主动压缩作业 — 挂 maintenance 窗。经总线 {@code llm.generate}，写回 {@code replaceRange}。
 * 不删审计事件。种子区不压。surface 条数 ≤ keepLast 则停（递减收益）。
 */
public final class CompactionWork implements MaintenanceWork {

    public static final int DEFAULT_KEEP_LAST = 4;
    private static final Logger LOG = System.getLogger(CompactionWork.class.getName());
    private static final String COMPONENT = "loop";
    private static final String CLASS_NAME = CompactionWork.class.getSimpleName();

    private final CapabilityBus bus;
    private final String model;
    private final String family;
    private final int keepLast;

    public CompactionWork(CapabilityBus bus, String model) {
        this(bus, model, "anthropic", DEFAULT_KEEP_LAST);
    }

    public CompactionWork(CapabilityBus bus, String model, String family, int keepLast) {
        this.bus = Objects.requireNonNull(bus, "bus");
        this.model = Objects.requireNonNull(model, "model");
        if (model.isBlank()) {
            throw new IllegalArgumentException("model blank");
        }
        this.family = family == null || family.isBlank() ? "anthropic" : family;
        if (keepLast < 1) {
            throw new IllegalArgumentException("keepLast < 1");
        }
        this.keepLast = keepLast;
    }

    @Override
    public boolean step(Session session, TurnContext ctx) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(ctx, "ctx");
        List<SessionEvent> live = liveSurface(session);
        if (live.size() <= keepLast) {
            return false;
        }
        List<SessionEvent> prefix = live.subList(0, live.size() - keepLast);
        if (prefix.stream().allMatch(e -> e.type() == SessionEventType.COMPACTION_CHECKPOINT)) {
            return false;
        }
        SessionEvent from = prefix.get(0);
        SessionEvent to = prefix.get(prefix.size() - 1);
        if (from.seq() > to.seq()) {
            return false;
        }
        Map<String, String> args = new LinkedHashMap<>();
        args.put("model", model);
        args.put("family", family);
        args.put("system", "compaction");
        SyscallResult result = bus.invoke(ctx, new Syscall(SessionLoop.SYSCALL_GENERATE, args));
        if (!result.ok()) {
            LOG.log(Level.WARNING, "component={0} class={1} session={2} compact llm failed code={3}",
                    COMPONENT, CLASS_NAME, session.id().value(), result.errorCode().orElse("FAILED"));
            throw new IllegalStateException("compaction llm failed: "
                    + result.errorCode().orElse("FAILED"));
        }
        String body = result.output().isBlank() ? "(compacted)" : result.output();
        session.logReplace().replaceRange(from.seq(), to.seq(), body);
        LOG.log(Level.INFO, "component={0} class={1} session={2} replaceRange from={3} to={4}",
                COMPONENT, CLASS_NAME, session.id().value(),
                String.valueOf(from.seq()), String.valueOf(to.seq()));
        return liveSurface(session).size() > keepLast;
    }

    public static List<SessionEvent> liveSurface(Session session) {
        long seedEnd = session.seedEndSeq().orElse(0L);
        List<SessionEvent> live = new ArrayList<>();
        for (SessionEvent event : session.logReplace().surface()) {
            if (event.seq() > seedEnd) {
                live.add(event);
            }
        }
        return live;
    }
}
