package com.tepeu.os.session;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 会话投影辅助 — 从 entries 增量读 + 可选推入 {@link ProjectionBus}。
 * since 游标 = 持久 entry seq 同型；读的是真相，bus 仍只是通知。
 */
public final class SessionProjections {

    private SessionProjections() {
    }

    public static List<SessionEvent> since(Session session, long afterSeq) {
        Objects.requireNonNull(session, "session");
        List<SessionEvent> out = new ArrayList<>();
        for (SessionEvent event : session.log().readAll()) {
            if (event.seq() > afterSeq) {
                out.add(event);
            }
        }
        return List.copyOf(out);
    }

    /** 将 (afterSeq, latest] 事件推入 bus；返回新游标（最后一条 seq，无新事件则原样）。 */
    public static long publishCatchUp(Session session, ProjectionBus bus, long afterSeq) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(bus, "bus");
        long cursor = afterSeq;
        for (SessionEvent event : since(session, afterSeq)) {
            bus.publish(session.id(), event);
            cursor = event.seq();
        }
        return cursor;
    }
}
