package com.tepeu.controller;

import com.tepeu.agent.multi.Goal;
import com.tepeu.agent.multi.MultiAgentOrchestrator;
import com.tepeu.agent.tool.ToolEventEmitter;
import com.tepeu.dto.MultiAgentRunRequest;
import com.tepeu.service.BudgetService;
import com.tepeu.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 多 Agent SSE：{@code POST /api/multi-agent/stream}。
 * 关联：MultiAgentOrchestrator、SessionService。
 */
@RestController
@RequestMapping("/api/multi-agent")
public class MultiAgentController {

    private static final Logger log = LoggerFactory.getLogger(MultiAgentController.class);
    private static final Long SSE_TIMEOUT_MS = 10L * 60 * 1000;

    private final MultiAgentOrchestrator orchestrator;
    private final SessionService sessionService;
    private final BudgetService budgetService;
    private final ObjectMapper objectMapper;

    public MultiAgentController(
            MultiAgentOrchestrator orchestrator,
            SessionService sessionService,
            BudgetService budgetService,
            ObjectMapper objectMapper) {
        this.orchestrator = orchestrator;
        this.sessionService = sessionService;
        this.budgetService = budgetService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/stream")
    public SseEmitter stream(@RequestBody MultiAgentRunRequest req) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        final Object sendLock = new Object();

        String err = validate(req);
        if (err != null) {
            send(emitter, sendLock, Map.of("type", "error", "code", "VALIDATION_ERROR", "message", err));
            emitter.complete();
            return emitter;
        }

        String workspaceId = req.getWorkspaceId().trim();
        String provider = req.getProvider().trim();
        try {
            if (budgetService.isBlocked(workspaceId)) {
                send(emitter, sendLock, Map.of(
                        "type", "error",
                        "code", "BUDGET_EXCEEDED",
                        "message", "工作区预算已用尽：请提高限额或关闭硬门禁后再试"));
                emitter.complete();
                return emitter;
            }
        } catch (IllegalArgumentException e) {
            // 不存在的 workspaceId：SSE error 事件，而非 JSON 400（前端按 SSE 解析）
            send(emitter, sendLock, Map.of("type", "error", "code", "NOT_FOUND",
                    "message", e.getMessage() == null ? "工作区不存在" : e.getMessage()));
            emitter.complete();
            return emitter;
        }
        String sessionId = req.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = sessionService.createSession(workspaceId, "Multi: " + trimTitle(req.getGoal())).getId();
        } else {
            var existing = sessionService.getSession(sessionId);
            if (existing.isEmpty()) {
                send(emitter, sendLock, Map.of("type", "error", "code", "NOT_FOUND",
                        "message", "会话不存在：" + sessionId));
                emitter.complete();
                return emitter;
            }
            // 会话↔工作区归属校验：工具根与消息归属必须一致（对齐 ChatController）
            if (workspaceId != null && !workspaceId.isBlank()
                    && !workspaceId.equals(existing.get().getWorkspaceId())) {
                send(emitter, sendLock, Map.of("type", "error", "code", "WORKSPACE_MISMATCH",
                        "message", "会话不属于该工作区，请切换到正确工作区或新建会话"));
                emitter.complete();
                return emitter;
            }
        }
        final String resolvedSessionId = sessionId;

        Goal goal;
        try {
            goal = new Goal(req.getGoal().trim(), req.getAcceptanceCriteria());
        } catch (IllegalArgumentException e) {
            send(emitter, sendLock, Map.of("type", "error", "code", "VALIDATION_ERROR",
                    "message", e.getMessage()));
            emitter.complete();
            return emitter;
        }

        ToolEventEmitter toolEvents = ToolEventEmitter.forSse(emitter, sendLock, objectMapper);
        AtomicReference<Thread> worker = new AtomicReference<>();

        Thread t = Thread.ofVirtual().name("multi-agent-" + resolvedSessionId).start(() -> {
            try {
                send(emitter, sendLock, Map.of("type", "session", "sessionId", resolvedSessionId));
                sessionService.appendMessage(resolvedSessionId, "user",
                        "[Multi-Agent Goal]\n" + goal.objective()
                                + "\n[Acceptance]\n" + goal.acceptanceCriteria());

                String status = orchestrator.run(
                        provider, goal, workspaceId, resolvedSessionId, toolEvents,
                        event -> send(emitter, sendLock, event));

                sessionService.appendMessage(resolvedSessionId, "assistant",
                        "[Multi-Agent] status=" + status);
            } catch (RuntimeException e) {
                log.error("Multi-agent run failed: {}", e.toString(), e);
                send(emitter, sendLock, Map.of(
                        "type", "error",
                        "code", "MULTI_AGENT_FAILED",
                        "message", e.getMessage() == null ? "run failed" : e.getMessage()));
            } finally {
                emitter.complete();
            }
        });
        worker.set(t);

        emitter.onCompletion(() -> interruptQuietly(worker.get()));
        emitter.onTimeout(() -> {
            interruptQuietly(worker.get());
            emitter.complete();
        });
        emitter.onError(ex -> interruptQuietly(worker.get()));

        return emitter;
    }

    private static String validate(MultiAgentRunRequest req) {
        if (req == null) return "请求体不能为空";
        if (req.getGoal() == null || req.getGoal().isBlank()) return "请填写目标";
        if (req.getWorkspaceId() == null || req.getWorkspaceId().isBlank()) return "需要指定工作区";
        if (req.getProvider() == null || req.getProvider().isBlank()) return "请选择模型服务商";
        return null;
    }

    private static String trimTitle(String goal) {
        if (goal == null) return "Multi-Agent";
        String t = goal.trim();
        return t.length() <= 40 ? t : t.substring(0, 40);
    }

    private void send(SseEmitter emitter, Object lock, Map<String, Object> payload) {
        synchronized (lock) {
            try {
                String json = objectMapper.writeValueAsString(payload);
                emitter.send(SseEmitter.event().name("message").data(json));
            } catch (IOException | RuntimeException e) {
                log.debug("SSE send skipped: {}", e.toString());
            }
        }
    }

    private static void interruptQuietly(Thread t) {
        if (t != null && t.isAlive()) {
            t.interrupt();
        }
    }
}
