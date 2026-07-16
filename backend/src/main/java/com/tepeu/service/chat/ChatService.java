package com.tepeu.service.chat;

import com.tepeu.agent.tool.ToolEventEmittingCallback;
import com.tepeu.agent.tool.ToolEventEmitter;
import com.tepeu.agent.tool.ToolRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
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

import java.util.List;

/**
 * Streams chat completions through Spring AI 2.0 {@link ChatModel}s built per provider from
 * DB-stored credentials (see {@link ChatModelFactory}).
 *
 * <p>ATE experimental：工具列表来自 {@link ToolRegistry}（见 {@code com.tepeu.agent.Tools}），
 * 本类不再硬编码依赖每个工具类型。调用路径见 {@code agent/AGENT_CALL_PATH.md}。
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatModelFactory factory;
    /** 显式注册的工具清单（唯一来源）。 */
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public ChatService(ChatModelFactory factory, ToolRegistry toolRegistry, ObjectMapper objectMapper) {
        this.factory = factory;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
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
        return streamWithTools(providerId, prompt, ToolEventEmitter.NOOP);
    }

    public Flux<ChatResponse> streamWithTools(String providerId, Prompt prompt, ToolEventEmitter emitter) {
        return Flux.defer(() -> {
            ChatModel model = factory.getChatModel(providerId);
            // 工具只从 ToolRegistry 读取；新增工具改 Tools.java，不必改这里
            ToolCallback[] wrapped = ToolEventEmittingCallback.wrapAll(
                    ToolCallbacks.from(toolRegistry.beans()), emitter, objectMapper);
            return ChatClient.builder(model).build()
                    .prompt(prompt)
                    .toolCallbacks(wrapped)
                    .stream()
                    .chatResponse();
        });
    }

    /**
     * Probe credentials with a minimal round-trip.
     * @return null on success; otherwise a stable error code or short message for the UI.
     */
    public String testConnection(String providerId) {
        try {
            ChatModel model = factory.getChatModel(providerId);
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
        ChatModel model = factory.getChatModel(providerId);
        Prompt prompt = new Prompt(new UserMessage(userMessage));
        ChatResponse response = model.call(prompt);
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }
}
