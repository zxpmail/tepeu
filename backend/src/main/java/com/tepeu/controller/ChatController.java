package com.tepeu.controller;

import com.tepeu.agent.AgentOrchestrator;
import com.tepeu.agent.tool.ToolEventEmitter;
import com.tepeu.dto.ChatRequest;
import com.tepeu.model.Message;
import com.tepeu.service.IdempotencyService;
import com.tepeu.service.SessionService;
import com.tepeu.service.TaskService;
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
 * Emits token / tool_call / tool_result / file_changed / usage / final / error.
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private static final Long SSE_TIMEOUT_MS = 5L * 60 * 1000;

    /** 粗略单价（USD / 1M tokens），未知 provider 时 cost 为 0 */
    private static final Map<String, double[]> PRICE_PER_MILLION = Map.of(
            "openai", new double[]{0.15, 0.60},
            "anthropic", new double[]{0.80, 4.00},
            "deepseek", new double[]{0.14, 0.28},
            "ollama", new double[]{0.0, 0.0}
    );

    private final AgentOrchestrator orchestrator;
    private final SessionService sessionService;
    private final TaskService taskService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    public ChatController(AgentOrchestrator orchestrator,
                          SessionService sessionService,
                          TaskService taskService,
                          IdempotencyService idempotencyService,
                          ObjectMapper objectMapper) {
        this.orchestrator = orchestrator;
        this.sessionService = sessionService;
        this.taskService = taskService;
        this.idempotencyService = idempotencyService;
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
        if (sessionId == null || sessionId.isBlank()) {
            if (workspaceId == null || workspaceId.isBlank()) {
                sendErrorEvent(emitter, sendLock, "VALIDATION_ERROR", "workspaceId is required when sessionId is absent");
                emitter.complete();
                return emitter;
            }
            sessionId = sessionService.createSession(workspaceId, deriveTitle(req.getMessage())).getId();
        } else {
            var existing = sessionService.getSession(sessionId);
            if (existing.isEmpty()) {
                sendErrorEvent(emitter, sendLock, "NOT_FOUND", "Session not found: " + sessionId);
                emitter.complete();
                return emitter;
            }
            if (workspaceId == null || workspaceId.isBlank()) {
                workspaceId = existing.get().getWorkspaceId();
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
                    "A request with the same idempotencyKey is already running");
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

        sessionService.appendMessage(resolvedSessionId, "user", req.getMessage());

        List<Message> history = sessionService.listMessages(resolvedSessionId);
        final StringBuilder assistantText = new StringBuilder();
        final AtomicReference<reactor.core.Disposable> subscription = new AtomicReference<>();
        final AtomicReference<Usage> lastUsage = new AtomicReference<>();
        final AtomicReference<String> lastModel = new AtomicReference<>();

        final ToolEventEmitter toolEvents = ToolEventEmitter.forSse(emitter, sendLock, objectMapper);

        reactor.core.publisher.Flux<ChatResponse> flux =
                orchestrator.streamTurn(providerId, history, toolEvents, req.getFileRefs(),
                        resolvedWorkspaceId, req.getSkillRefs());

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
                    idempotencyService.release(idemKey);
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
                    sendEvent(emitter, sendLock, Map.of("type", "final"));
                    emitter.complete();
                }
        ));

        emitter.onCompletion(() -> disposeQuietly(subscription.get()));
        emitter.onTimeout(() -> {
            disposeQuietly(subscription.get());
            emitter.complete();
        });
        emitter.onError(t -> disposeQuietly(subscription.get()));

        return emitter;
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
        double cost = estimateCost(providerId, prompt, completion);

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

    private static double estimateCost(String providerId, int prompt, int completion) {
        double[] price = PRICE_PER_MILLION.getOrDefault(
                providerId == null ? "" : providerId.toLowerCase(),
                new double[]{0.0, 0.0});
        return (prompt * price[0] + completion * price[1]) / 1_000_000.0;
    }

    private static String validate(ChatRequest req) {
        if (req == null) return "Request body is required";
        if (req.getMessage() == null || req.getMessage().isBlank()) return "message is required";
        if (req.getProvider() == null || req.getProvider().isBlank()) return "provider is required";
        return null;
    }

    private static String deriveTitle(String message) {
        if (message == null) return "New Chat";
        String oneLine = message.replaceAll("\\s+", " ").trim();
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
        return new String[]{"CHAT_ERROR", "Chat request failed"};
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
            case "UNKNOWN_PROVIDER" -> "Unknown provider";
            case "UNSUPPORTED_PROVIDER" -> "Provider is not supported";
            case "PROVIDER_DISABLED" -> "Provider is disabled";
            case "MISSING_API_KEY" -> "Provider API key is not configured";
            case "MISSING_MODEL" -> "Provider model is not configured";
            case "API_KEY_LOOKS_LIKE_URL" -> "API Key was saved as a URL; paste the real key into API Key field";
            default -> "Chat request failed";
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
            return new String[]{"AUTH_FAILED", "API key invalid or unauthorized"};
        }
        if (blob.contains("403") || blob.contains("forbidden") || blob.contains("not allowed")) {
            return new String[]{"FORBIDDEN", "Provider refused the request (403)"};
        }
        if (blob.contains("404") && blob.contains("model")) {
            return new String[]{"MODEL_NOT_FOUND", "Configured model was not found"};
        }
        if (blob.contains("model") && (blob.contains("not found") || blob.contains("does not exist")
                || blob.contains("does not support"))) {
            return new String[]{"MODEL_NOT_FOUND", "Configured model was not found"};
        }
        if (blob.contains("connection refused") || blob.contains("timed out")
                || blob.contains("timeout") || blob.contains("unknown host")
                || blob.contains("failed to connect")) {
            return new String[]{"NETWORK_ERROR", "Cannot reach the provider endpoint"};
        }
        if (blob.contains("credential source must be specified")) {
            return new String[]{"PROVIDER_MISCONFIGURED", "Provider client is missing credentials"};
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
