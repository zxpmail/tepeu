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

    /** 模型可见序列：对话两方按日志序；工具、推理、计划第一刀不可见。 */
    public static List<LlmMessage> visible(List<SessionEvent> events) {
        Objects.requireNonNull(events, "events");
        return events.stream()
                .filter(e -> e.type() == SessionEventType.USER_MESSAGE
                        || e.type() == SessionEventType.ASSISTANT_MESSAGE)
                .map(e -> new LlmMessage(
                        e.type() == SessionEventType.USER_MESSAGE ? Role.USER : Role.ASSISTANT,
                        e.body()))
                .toList();
    }
}
