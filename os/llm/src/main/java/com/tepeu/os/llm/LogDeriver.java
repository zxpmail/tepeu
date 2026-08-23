package com.tepeu.os.llm;

import com.tepeu.os.session.SessionEvent;
import com.tepeu.os.session.SessionEventType;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志 → canonical。模型可见 7 类全定义；END_SEED 跳过。非日志可派生者发不出去（第十轮）。
 * 读模型面（surface），不是审计全量。END_SEED 不进模型。
 */
public final class LogDeriver {

    public static final String VERSION = "1";

    private LogDeriver() {
    }

    public static List<CanonicalTurn> derive(List<SessionEvent> surface) {
        List<CanonicalTurn> out = new ArrayList<>(surface.size());
        for (SessionEvent event : surface) {
            if (event.type() == SessionEventType.END_SEED) {
                continue;
            }
            out.add(map(event));
        }
        return List.copyOf(out);
    }

    private static CanonicalTurn map(SessionEvent event) {
        return switch (event.type()) {
            case USER_MESSAGE -> new CanonicalTurn(CanonicalRole.USER, event.type(), event.body(), event.attrs());
            case ASSISTANT_MESSAGE, TOOL_CALL, PLAN_STEP, COMPACTION_CHECKPOINT ->
                    new CanonicalTurn(CanonicalRole.ASSISTANT, event.type(), event.body(), event.attrs());
            case TOOL_RESULT -> new CanonicalTurn(CanonicalRole.TOOL, event.type(), event.body(), event.attrs());
            case REASONING -> new CanonicalTurn(CanonicalRole.REASONING, event.type(), event.body(), event.attrs());
            case END_SEED -> throw new IllegalStateException("END_SEED is not model-visible");
        };
    }
}
