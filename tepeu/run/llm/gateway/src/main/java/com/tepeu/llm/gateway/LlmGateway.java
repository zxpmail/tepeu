package com.tepeu.llm.gateway;

import com.tepeu.session.SessionEvent;
import com.tepeu.session.SessionEventType;
import com.tepeu.session.SessionLog;
import com.tepeu.syscall.SyscallResult;

import java.util.List;
import java.util.Objects;

/**
 * 统一调用口。loop / commands 只进这里，不碰后端实现。
 * {@link #visible} 是纯函数：事件日志 → 模型可见序列，可测试相等。
 * 结果与用量原样透传后端返回值，不追加事件、不记用量。
 */
public final class LlmGateway {

    private final LlmBackend backend;

    public LlmGateway(LlmBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    /** 派生可见序列，交后端生成。 */
    public SyscallResult generate(SessionLog log) {
        Objects.requireNonNull(log, "log");
        return backend.generate(visible(log.readAll()));
    }

    /** 模型可见序列：对话两方与工具结果按日志序；工具请求、推理、计划不可见。 */
    public static List<LlmMessage> visible(List<SessionEvent> events) {
        Objects.requireNonNull(events, "events");
        return events.stream()
                .filter(e -> e.type() == SessionEventType.USER_MESSAGE
                        || e.type() == SessionEventType.ASSISTANT_MESSAGE
                        || e.type() == SessionEventType.TOOL_RESULT)
                .map(e -> new LlmMessage(roleOf(e.type()), e.body()))
                .toList();
    }

    private static Role roleOf(SessionEventType type) {
        return switch (type) {
            case USER_MESSAGE -> Role.USER;
            case ASSISTANT_MESSAGE -> Role.ASSISTANT;
            case TOOL_RESULT -> Role.TOOL;
            default -> throw new IllegalStateException("not visible: " + type);
        };
    }
}
