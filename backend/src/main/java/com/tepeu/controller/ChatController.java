package com.tepeu.controller;

import com.tepeu.agent.AgentOrchestrator;
import com.tepeu.agent.hook.HallucinationGuard;
import com.tepeu.agent.tool.ToolEventEmitter;
import com.tepeu.dto.ChatRequest;
import com.tepeu.model.Message;
import com.tepeu.service.BudgetService;
import com.tepeu.service.IdempotencyService;
import com.tepeu.service.SessionService;
import com.tepeu.service.TaskService;
import com.tepeu.service.TokenCostEstimator;
import com.tepeu.service.ToolTraceCodec;
import com.tepeu.service.WorkspacePathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Streaming chat endpoint: {@code POST /api/chat/stream} → {@link SseEmitter}.
 * Emits token / tool_call / tool_result / tool_approval_required / tool_denied /
 * file_changed / usage / final / error.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final Long SSE_TIMEOUT_MS = 5L * 60 * 1000;

    private final AgentOrchestrator orchestrator;
    private final SessionService sessionService;
    private final TaskService taskService;
    private final IdempotencyService idempotencyService;
    private final BudgetService budgetService;
    private final TokenCostEstimator costEstimator;
    private final HallucinationGuard hallucinationGuard;
    private final WorkspacePathResolver pathResolver;
    private final ObjectMapper objectMapper;

    public ChatController(AgentOrchestrator orchestrator,
                          SessionService sessionService,
                          TaskService taskService,
                          IdempotencyService idempotencyService,
                          BudgetService budgetService,
                          TokenCostEstimator costEstimator,
                          HallucinationGuard hallucinationGuard,
                          WorkspacePathResolver pathResolver,
                          ObjectMapper objectMapper) {
        this.orchestrator = orchestrator;
        this.sessionService = sessionService;
        this.taskService = taskService;
        this.idempotencyService = idempotencyService;
        this.budgetService = budgetService;
        this.costEstimator = costEstimator;
        this.hallucinationGuard = hallucinationGuard;
        this.pathResolver = pathResolver;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/stream")
    public SseEmitter stream(@RequestBody ChatRequest req) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        final Object sendLock = new Object();

        String validationError = validate(req);
        if (validationError != null) {
            sendErrorEvent(emitter, sendLock, "VALIDATION_ERROR", validationError);
            emitter.complete();
            return emitter;
        }

        String sessionId = req.getSessionId();
        String workspaceId = req.getWorkspaceId();

        // 先解析 workspace，硬门禁通过后再建会话，避免空会话堆积
        if ((workspaceId == null || workspaceId.isBlank())
                && sessionId != null && !sessionId.isBlank()) {
            var existingForBudget = sessionService.getSession(sessionId);
            if (existingForBudget.isPresent()) {
                workspaceId = existingForBudget.get().getWorkspaceId();
            }
        }
        try {
            if (workspaceId != null && !workspaceId.isBlank() && budgetService.isBlocked(workspaceId)) {
                sendErrorEvent(emitter, sendLock, "BUDGET_EXCEEDED",
                        "工作区预算已用尽：请提高限额或关闭硬门禁后再试");
                emitter.complete();
                return emitter;
            }
        } catch (IllegalArgumentException e) {
            // 不存在的 workspaceId：以 SSE error 事件回给前端（而非 JSON 400，破坏流解析）
            sendErrorEvent(emitter, sendLock, "NOT_FOUND",
                    e.getMessage() == null ? "工作区不存在" : e.getMessage());
            emitter.complete();
            return emitter;
        }

        if (sessionId == null || sessionId.isBlank()) {
            if (workspaceId == null || workspaceId.isBlank()) {
                sendErrorEvent(emitter, sendLock, "VALIDATION_ERROR", "新建对话需要指定工作区");
                emitter.complete();
                return emitter;
            }
            sessionId = sessionService.createSession(workspaceId, deriveTitle(req.getMessage())).getId();
        } else {
            var existing = sessionService.getSession(sessionId);
            if (existing.isEmpty()) {
                sendErrorEvent(emitter, sendLock, "NOT_FOUND", "会话不存在：" + sessionId);
                emitter.complete();
                return emitter;
            }
            if (workspaceId == null || workspaceId.isBlank()) {
                workspaceId = existing.get().getWorkspaceId();
            } else if (!workspaceId.equals(existing.get().getWorkspaceId())) {
                sendErrorEvent(emitter, sendLock, "WORKSPACE_MISMATCH",
                        "会话不属于该工作区，请切换到正确工作区或新建对话");
                emitter.complete();
                return emitter;
            }
        }
        final String resolvedSessionId = sessionId;
        final String resolvedWorkspaceId = workspaceId;
        final String providerId = req.getProvider();
        final String idemKey = req.getIdempotencyKey();

        // 幂等：判断在基础设施层，不交给模型「自己想是否重复」
        IdempotencyService.AcquireResult acquire = idempotencyService.tryAcquire(idemKey);
        if (acquire.status() == IdempotencyService.AcquireStatus.IN_PROGRESS) {
            sendErrorEvent(emitter, sendLock, "IDEMPOTENCY_IN_PROGRESS",
                    "相同请求正在处理中，请稍候");
            emitter.complete();
            return emitter;
        }
        if (acquire.status() == IdempotencyService.AcquireStatus.REPLAY) {
            String cached = acquire.cachedText() == null ? "" : acquire.cachedText();
            if (!cached.isEmpty()) {
                sendEvent(emitter, sendLock, Map.of("type", "token", "content", cached));
            }
            sendEvent(emitter, sendLock, Map.of("type", "final", "idempotentReplay", true));
            emitter.complete();
            return emitter;
        }

        var userMsg = sessionService.appendMessage(resolvedSessionId, "user", req.getMessage());
        String userMessageId = userMsg == null ? null : userMsg.getId();

        List<Message> history = sessionService.listMessages(resolvedSessionId);
        final StringBuilder assistantText = new StringBuilder();
        final AtomicReference<reactor.core.Disposable> subscription = new AtomicReference<>();
        final AtomicReference<Usage> lastUsage = new AtomicReference<>();
        final AtomicReference<String> lastModel = new AtomicReference<>();

        final ToolEventEmitter sseTools = ToolEventEmitter.forSse(emitter, sendLock, objectMapper);
        final ToolEventEmitter toolEvents = event -> {
            sseTools.emit(event);
            persistToolTrace(resolvedSessionId, event);
        };

        reactor.core.publisher.Flux<ChatResponse> flux =
                orchestrator.streamTurn(providerId, history, toolEvents, req.getFileRefs(),
                        resolvedWorkspaceId, req.getSkillRefs(), resolvedSessionId);

        subscription.set(flux.subscribe(
                chunk -> {
                    captureUsage(chunk, lastUsage, lastModel);
                    String text = extractText(chunk);
                    if (text != null && !text.isEmpty()) {
                        assistantText.append(text);
                        sendEvent(emitter, sendLock, Map.of("type", "token", "content", text));
                    }
                },
                error -> {
                    log.error("Chat stream failed provider={} session={}: {}",
                            providerId, resolvedSessionId, error.toString(), error);
                    rollbackUserMessage(resolvedSessionId, userMessageId);
                    idempotencyService.release(idemKey);
                    persistPartialAssistant(resolvedSessionId, assistantText.toString());
                    String[] mapped = mapError(error);
                    sendErrorEvent(emitter, sendLock, mapped[0], mapped[1]);
                    emitter.complete();
                },
                () -> {
                    String reply = assistantText.toString();
                    if (!reply.isEmpty()) {
                        try {
                            sessionService.appendMessage(resolvedSessionId, "assistant", reply);
                        } catch (RuntimeException e) {
                            log.warn("Failed to persist assistant reply for session {}: {}", resolvedSessionId, e.getMessage());
                        }
                    }
                    idempotencyService.complete(idemKey, reply);
                    emitAndRecordUsage(emitter, sendLock, resolvedWorkspaceId, resolvedSessionId,
                            providerId, lastUsage.get(), lastModel.get());
                    emitHallucinationWarnings(emitter, sendLock, resolvedWorkspaceId, reply);
                    sendEvent(emitter, sendLock, Map.of("type", "final"));
                    emitter.complete();
                }
        ));

        emitter.onCompletion(() -> disposeQuietly(subscription.get()));
        emitter.onTimeout(() -> {
            disposeQuietly(subscription.get());
            rollbackUserMessage(resolvedSessionId, userMessageId);
            idempotencyService.release(idemKey);
            persistPartialAssistant(resolvedSessionId, assistantText.toString());
            sendErrorEvent(emitter, sendLock, "SSE_TIMEOUT", "对话超时（5 分钟），请重试");
            emitter.complete();
        });
        emitter.onError(t -> {
            disposeQuietly(subscription.get());
            rollbackUserMessage(resolvedSessionId, userMessageId);
            idempotencyService.release(idemKey);
            persistPartialAssistant(resolvedSessionId, assistantText.toString());
        });

        return emitter;
    }

    /** 持久化工具过程（system 行，刷新后可恢复卡片） */
    private void persistToolTrace(String sessionId, Map<String, Object> event) {
        String encoded = ToolTraceCodec.encode(event, objectMapper);
        if (encoded == null) return;
        try {
            sessionService.appendMessage(sessionId, "system", encoded);
        } catch (RuntimeException e) {
            log.debug("Tool trace persist skipped: {}", e.getMessage());
        }
    }

    /** stream 失败/超时时回滚刚插入的 user 消息，避免同幂等键重试产生重复消息。 */
    private void rollbackUserMessage(String sessionId, String messageId) {
        if (messageId == null) return;
        try {
            sessionService.deleteMessage(messageId);
        } catch (RuntimeException e) {
            log.debug("Rollback user message skipped session={}: {}", sessionId, e.getMessage());
        }
    }

    /** 错误/超时时尽量保存已生成的助手片段 */
    private void persistPartialAssistant(String sessionId, String reply) {
        if (reply == null || reply.isBlank()) return;
        try {
            sessionService.appendMessage(sessionId, "assistant", reply + "\n\n（回复未完整结束）");
        } catch (RuntimeException e) {
            log.debug("Partial assistant persist skipped: {}", e.getMessage());
        }
    }

    /** 扫描助手声称已写入但不存在的文件路径，发出幻觉警告。 */
    private void emitHallucinationWarnings(
            SseEmitter emitter, Object sendLock, String workspaceId, String reply) {
        if (reply == null || reply.isBlank() || workspaceId == null) {
            return;
        }
        try {
            var missing = hallucinationGuard.findMissingClaimedPaths(
                    reply, pathResolver.resolveBasePath(workspaceId));
            if (!missing.isEmpty()) {
                sendEvent(emitter, sendLock, Map.of(
                        "type", "hallucination_warning",
                        "message", "助手声称已写入但工作区中未找到的文件",
                        "missingPaths", missing));
            }
        } catch (RuntimeException e) {
            log.debug("Hallucination scan skipped: {}", e.getMessage());
        }
    }

    /** 从 chunk metadata 捕获 usage（流式结束块通常才有完整数字） */
    private static void captureUsage(ChatResponse chunk, AtomicReference<Usage> lastUsage,
                                     AtomicReference<String> lastModel) {
        if (chunk == null || chunk.getMetadata() == null) return;
        Usage usage = chunk.getMetadata().getUsage();
        if (usage != null) {
            Integer total = usage.getTotalTokens();
            if (total != null && total > 0) {
                lastUsage.set(usage);
            } else if (usage.getPromptTokens() != null || usage.getCompletionTokens() != null) {
                lastUsage.set(usage);
            }
        }
        String model = chunk.getMetadata().getModel();
        if (model != null && !model.isBlank()) {
            lastModel.set(model);
        }
    }

    /** 发送 usage SSE 并写入 task 表 */
    private void emitAndRecordUsage(SseEmitter emitter, Object sendLock, String workspaceId,
                                    String sessionId, String providerId, Usage usage, String model) {
        int prompt = usage != null && usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int completion = usage != null && usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        int total = usage != null && usage.getTotalTokens() != null
                ? usage.getTotalTokens() : prompt + completion;
        double cost = costEstimator.estimate(providerId, prompt, completion);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "usage");
        payload.put("promptTokens", prompt);
        payload.put("completionTokens", completion);
        payload.put("totalTokens", total);
        payload.put("costUsd", cost);
        sendEvent(emitter, sendLock, payload);

        if (workspaceId != null && total > 0) {
            try {
                taskService.recordTurn(workspaceId, sessionId, model != null ? model : providerId,
                        prompt, completion, cost);
            } catch (RuntimeException e) {
                log.debug("Failed to record turn usage: {}", e.getMessage());
            }
        }
    }

    private static String validate(ChatRequest req) {
        if (req == null) return "请求体不能为空";
        if (req.getMessage() == null || req.getMessage().isBlank()) return "请输入消息";
        if (req.getProvider() == null || req.getProvider().isBlank()) return "请选择模型服务商";
        return null;
    }

    private static String deriveTitle(String message) {
        if (message == null) return "新对话";
        String oneLine = message.replaceAll("\\s+", " ").trim();
        if (oneLine.isEmpty()) return "新对话";
        return oneLine.length() <= 40 ? oneLine : oneLine.substring(0, 40) + "…";
    }

    private static String extractText(ChatResponse chunk) {
        if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
            return null;
        }
        return chunk.getResult().getOutput().getText();
    }

    static String[] mapError(Throwable error) {
        if (error != null) {
            String code = error.getMessage();
            if (code != null && isKnownCode(code)) {
                return new String[]{code, describe(code)};
            }
            String[] classified = classifyProviderError(error);
            if (classified != null) {
                return classified;
            }
        }
        return new String[]{"CHAT_ERROR", "对话请求失败"};
    }

    private static boolean isKnownCode(String code) {
        return switch (code) {
            case "UNKNOWN_PROVIDER", "UNSUPPORTED_PROVIDER", "PROVIDER_DISABLED",
                 "MISSING_API_KEY", "MISSING_MODEL", "API_KEY_LOOKS_LIKE_URL" -> true;
            default -> false;
        };
    }

    private static String describe(String code) {
        return switch (code) {
            case "UNKNOWN_PROVIDER" -> "未知服务商";
            case "UNSUPPORTED_PROVIDER" -> "不支持的服务商";
            case "PROVIDER_DISABLED" -> "服务商未启用";
            case "MISSING_API_KEY" -> "尚未配置 API Key";
            case "MISSING_MODEL" -> "尚未配置默认模型";
            case "API_KEY_LOOKS_LIKE_URL" -> "API Key 被存成了网址，请重新粘贴真正的密钥";
            default -> "对话请求失败";
        };
    }

    /**
     * 把上游 LLM/HTTP 异常归类成稳定、可展示、不泄漏密钥的文案。
     * 原始异常细节只写服务端日志。
     */
    static String[] classifyProviderError(Throwable error) {
        String blob = joinMessages(error).toLowerCase();
        if (blob.contains("401") || blob.contains("unauthorized")
                || blob.contains("authentication") || blob.contains("invalid api key")
                || blob.contains("incorrect api key")) {
            return new String[]{"AUTH_FAILED", "API Key 无效或未授权"};
        }
        if (blob.contains("403") || blob.contains("forbidden") || blob.contains("not allowed")) {
            return new String[]{"FORBIDDEN", "服务商拒绝了请求（403）"};
        }
        if (blob.contains("404") && blob.contains("model")) {
            return new String[]{"MODEL_NOT_FOUND", "配置的模型不存在"};
        }
        if (blob.contains("model") && (blob.contains("not found") || blob.contains("does not exist")
                || blob.contains("does not support"))) {
            return new String[]{"MODEL_NOT_FOUND", "配置的模型不存在"};
        }
        if (blob.contains("connection refused") || blob.contains("timed out")
                || blob.contains("timeout") || blob.contains("unknown host")
                || blob.contains("failed to connect")) {
            return new String[]{"NETWORK_ERROR", "无法连接服务商，请检查网络或 Base URL"};
        }
        if (blob.contains("credential source must be specified")) {
            return new String[]{"PROVIDER_MISCONFIGURED", "服务商凭证配置不完整"};
        }
        return null;
    }

    /** 拼接 cause 链消息；顺带抹掉疑似 API key，避免误入 SSE。 */
    private static String joinMessages(Throwable error) {
        StringBuilder sb = new StringBuilder();
        Throwable cur = error;
        int depth = 0;
        while (cur != null && depth < 8) {
            if (cur.getMessage() != null) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(cur.getMessage());
            }
            cur = cur.getCause();
            depth++;
        }
        return sb.toString().replaceAll("sk-[A-Za-z0-9_-]+", "sk-***");
    }

    private void sendEvent(SseEmitter emitter, Object monitor, Map<String, Object> payload) {
        synchronized (monitor) {
            try {
                String json = objectMapper.writeValueAsString(payload);
                emitter.send(SseEmitter.event().name("message").data(json));
            } catch (IOException e) {
                log.debug("SSE send failed (client disconnected?): {}", e.getMessage());
            } catch (RuntimeException e) {
                log.warn("Failed to serialize SSE payload: {}", e.getMessage());
            }
        }
    }

    private void sendErrorEvent(SseEmitter emitter, Object monitor, String code, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "error");
        payload.put("code", code);
        payload.put("message", message);
        sendEvent(emitter, monitor, payload);
    }

    private static void disposeQuietly(reactor.core.Disposable disposable) {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }
}
