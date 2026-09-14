package com.tepeu.llm.gateway;

import com.tepeu.session.SessionEvent;
import com.tepeu.session.SessionEventType;
import com.tepeu.session.SessionLog;
import com.tepeu.syscall.SyscallResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 统一调用口。loop / commands 只进这里，不碰后端实现。
 * 系统提示由 load 装配时给出（可空），排在可见序列头部。
 * {@link #visible} 是纯函数：系统提示 + 事件日志 → 模型可见序列，可测试相等。
 * 问句（{@code /btw}）只进可见序列尾部，不写事件日志。
 * 结果与用量原样透传后端返回值，不追加事件、不记用量。
 */
public final class LlmGateway {

    private final LlmBackend backend;
    private final String systemPrompt;

    public LlmGateway(LlmBackend backend, String systemPrompt) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.systemPrompt = systemPrompt == null || systemPrompt.isBlank() ? null : systemPrompt;
    }

    /** 派生可见序列，交后端生成。问句非空白时作 USER 消息尾插，不写账。 */
    public SyscallResult generate(SessionLog log, String question) {
        Objects.requireNonNull(log, "log");
        List<LlmMessage> messages = new ArrayList<>(visible(systemPrompt, log.readAll()));
        if (question != null && !question.isBlank()) {
            messages.add(new LlmMessage(Role.USER, question));
        }
        return backend.generate(List.copyOf(messages));
    }

    /** 模型可见序列：系统提示在头，对话两方与工具结果按日志序；工具请求、推理、计划不可见。 */
    public static List<LlmMessage> visible(String systemPrompt, List<SessionEvent> events) {
        Objects.requireNonNull(events, "events");
        List<LlmMessage> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new LlmMessage(Role.SYSTEM, systemPrompt));
        }
        events.stream()
                .filter(e -> e.type() == SessionEventType.USER_MESSAGE
                        || e.type() == SessionEventType.ASSISTANT_MESSAGE
                        || e.type() == SessionEventType.TOOL_RESULT)
                .map(e -> new LlmMessage(roleOf(e.type()), e.body()))
                .forEach(messages::add);
        return List.copyOf(messages);
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
