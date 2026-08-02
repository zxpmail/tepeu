package com.tepeu.service.chat;

import com.tepeu.agent.hook.ApprovalStore;
import com.tepeu.agent.hook.HookingToolCallback;
import com.tepeu.agent.hook.ToolHook;
import com.tepeu.agent.mcp.McpToolBridge;
import com.tepeu.agent.tool.ToolEventEmittingCallback;
import com.tepeu.agent.tool.ToolEventEmitter;
import com.tepeu.agent.tool.ToolRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.support.ToolCallbacks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Streams chat completions through Spring AI 2.0 {@link ChatModel}s built per provider from
 * DB-stored credentials (see {@link ChatModelFactory}).
 *
 * <p>工具 = {@link ToolRegistry} + {@link McpToolBridge}；装饰链：Hook → 可视化。
 * 调用路径见 {@code agent/AGENT_CALL_PATH.md}。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatModelFactory factory;
    /** 本地 @Tool 显式清单。 */
    private final ToolRegistry toolRegistry;
    private final McpToolBridge mcpToolBridge;
    private final ObjectMapper objectMapper;
    private final ToolHook toolHook;
    private final ApprovalStore approvalStore;

    public ChatService(
            ChatModelFactory factory,
            ToolRegistry toolRegistry,
            McpToolBridge mcpToolBridge,
            ObjectMapper objectMapper,
            ToolHook toolHook,
            ApprovalStore approvalStore) {
        this.factory = factory;
        this.toolRegistry = toolRegistry;
        this.mcpToolBridge = mcpToolBridge;
        this.objectMapper = objectMapper;
        this.toolHook = toolHook;
        this.approvalStore = approvalStore;
    }

    public Flux<ChatResponse> stream(String providerId, String userMessage) {
        return Flux.defer(() -> {
            ChatModel model = factory.getChatModel(providerId);
            Prompt prompt = new Prompt(new UserMessage(userMessage));
            return model.stream(prompt);
        });
    }

    public Flux<ChatResponse> stream(String providerId, Prompt prompt) {
        return Flux.defer(() -> {
            ChatModel model = factory.getChatModel(providerId);
            return model.stream(prompt);
        });
    }

    public Flux<ChatResponse> streamWithTools(String providerId, Prompt prompt) {
        return streamWithTools(providerId, prompt, ToolEventEmitter.NOOP, null);
    }

    public Flux<ChatResponse> streamWithTools(String providerId, Prompt prompt, ToolEventEmitter emitter) {
        return streamWithTools(providerId, prompt, emitter, null, null);
    }

    /**
     * 带工具的流式对话。装饰顺序：本地+MCP ToolCallback → Hook（内）→ 事件可视化（外）。
     */
    public Flux<ChatResponse> streamWithTools(
            String providerId, Prompt prompt, ToolEventEmitter emitter, String sessionId) {
        return streamWithTools(providerId, prompt, emitter, sessionId, null);
    }

    /**
     * @param allowedToolNames 非 null 时仅暴露名单内工具（Reviewer 只读面）
     */
    public Flux<ChatResponse> streamWithTools(
            String providerId,
            Prompt prompt,
            ToolEventEmitter emitter,
            String sessionId,
            Set<String> allowedToolNames) {
        return Flux.defer(() -> {
            ChatModel model = factory.getChatModel(providerId);
            ToolEventEmitter sink = emitter != null ? emitter : ToolEventEmitter.NOOP;
            ToolCallback[] raw = filterTools(mergeToolCallbacks(), allowedToolNames);
            ToolCallback[] hooked = HookingToolCallback.wrapAll(
                    raw, toolHook, approvalStore, sessionId, sink);
            ToolCallback[] wrapped = ToolEventEmittingCallback.wrapAll(hooked, sink, objectMapper);
            return ChatClient.builder(model).build()
                    .prompt(prompt)
                    .toolCallbacks(wrapped)
                    .stream()
                    .chatResponse();
        });
    }

    /** 本地 ToolRegistry + MCP 桥接工具。 */
    ToolCallback[] mergeToolCallbacks() {
        ToolCallback[] local = ToolCallbacks.from(toolRegistry.beans());
        ToolCallback[] mcp = mcpToolBridge.callbacks();
        if (mcp.length == 0) {
            return local;
        }
        ToolCallback[] merged = Arrays.copyOf(local, local.length + mcp.length);
        System.arraycopy(mcp, 0, merged, local.length, mcp.length);
        return merged;
    }

    /** 按工具名白名单过滤；null/空表示不过滤。 */
    static ToolCallback[] filterTools(ToolCallback[] all, Set<String> allowedToolNames) {
        if (allowedToolNames == null || allowedToolNames.isEmpty() || all == null) {
            return all == null ? new ToolCallback[0] : all;
        }
        List<ToolCallback> kept = new ArrayList<>();
        for (ToolCallback cb : all) {
            if (cb != null && allowedToolNames.contains(cb.getToolDefinition().name())) {
                kept.add(cb);
            }
        }
        return kept.toArray(ToolCallback[]::new);
    }

    /**
     * Probe credentials with a minimal round-trip.
     * @return null on success; otherwise a stable error code or short message for the UI.
     */
    public String testConnection(String providerId) {
        return testConnection(providerId, null, null, null);
    }

    /**
     * 连接测试；可选草稿凭证覆盖（未保存前也可测）。
     * @return null 表示成功；否则为稳定错误码
     */
    public String testConnection(
            String providerId, String apiKeyOverride, String baseUrlOverride, String modelOverride) {
        try {
            boolean hasOverride = (apiKeyOverride != null && !apiKeyOverride.isBlank())
                    || (baseUrlOverride != null && !baseUrlOverride.isBlank())
                    || (modelOverride != null && !modelOverride.isBlank());
            ChatModel model = hasOverride
                    ? factory.getChatModelWithOverrides(providerId, apiKeyOverride, baseUrlOverride, modelOverride)
                    : factory.getChatModel(providerId);
            ChatResponse response = model.call(new Prompt(new UserMessage("ping")));
            if (response != null && response.getResult() != null) {
                return null;
            }
            return "CONNECTION_FAILED";
        } catch (IllegalArgumentException | IllegalStateException e) {
            String code = e.getMessage() != null ? e.getMessage() : "CONNECTION_FAILED";
            log.warn("Provider {} connection test failed: {}", providerId, code);
            return code;
        } catch (RuntimeException e) {
            log.warn("Provider {} connection test failed: {}", providerId, e.toString(), e);
            return "CONNECTION_FAILED";
        }
    }

    List<ToolCallback> toolCallbacks() {
        return List.of(ToolCallbacks.from(toolRegistry.beans()));
    }

    public String chat(String providerId, String userMessage) {
        return chat(providerId, new Prompt(new UserMessage(userMessage)));
    }

    /** 同步回合结果（文本 + token 用量，供多 Agent 入账）。 */
    public record TurnResult(String text, int promptTokens, int completionTokens) {}

    /** 无工具同步调用（Planner）。 */
    public String chat(String providerId, Prompt prompt) {
        return chatTurn(providerId, prompt).text();
    }

    /** 无工具同步调用并返回用量。 */
    public TurnResult chatTurn(String providerId, Prompt prompt) {
        ChatModel model = factory.getChatModel(providerId);
        ChatResponse response = model.call(prompt);
        String text = "";
        if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
            String t = response.getResult().getOutput().getText();
            text = t == null ? "" : t;
        }
        return new TurnResult(text, promptTokens(response), completionTokens(response));
    }

    /**
     * 带工具同步跑完一轮（Implementer / Reviewer）；工具事件经 emitter 转发。
     * 共享 Hook / ToolRegistry 执行面。
     */
    public String chatWithTools(
            String providerId, Prompt prompt, ToolEventEmitter emitter, String sessionId) {
        return chatWithToolsTurn(providerId, prompt, emitter, sessionId).text();
    }

    /** 带工具同步跑完一轮并返回用量。 */
    public TurnResult chatWithToolsTurn(
            String providerId, Prompt prompt, ToolEventEmitter emitter, String sessionId) {
        return chatWithToolsTurn(providerId, prompt, emitter, sessionId, null);
    }

    /** 带工具同步跑完一轮；可选工具白名单。 */
    public TurnResult chatWithToolsTurn(
            String providerId,
            Prompt prompt,
            ToolEventEmitter emitter,
            String sessionId,
            Set<String> allowedToolNames) {
        StringBuilder sb = new StringBuilder();
        AtomicReference<Usage> lastUsage = new AtomicReference<>();
        streamWithTools(providerId, prompt, emitter, sessionId, allowedToolNames)
                .doOnNext(chunk -> {
                    if (chunk == null) {
                        return;
                    }
                    if (chunk.getMetadata() != null && chunk.getMetadata().getUsage() != null) {
                        lastUsage.set(chunk.getMetadata().getUsage());
                    }
                    if (chunk.getResult() == null || chunk.getResult().getOutput() == null) {
                        return;
                    }
                    String t = chunk.getResult().getOutput().getText();
                    if (t != null && !t.isEmpty()) {
                        sb.append(t);
                    }
                })
                .blockLast();
        Usage u = lastUsage.get();
        int promptTok = u != null && u.getPromptTokens() != null ? u.getPromptTokens() : 0;
        int completionTok = u != null && u.getCompletionTokens() != null ? u.getCompletionTokens() : 0;
        return new TurnResult(sb.toString(), promptTok, completionTok);
    }

    private static int promptTokens(ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return 0;
        }
        Integer n = response.getMetadata().getUsage().getPromptTokens();
        return n == null ? 0 : n;
    }

    private static int completionTokens(ChatResponse response) {
        if (response == null || response.getMetadata() == null || response.getMetadata().getUsage() == null) {
            return 0;
        }
        Integer n = response.getMetadata().getUsage().getCompletionTokens();
        return n == null ? 0 : n;
    }
}
