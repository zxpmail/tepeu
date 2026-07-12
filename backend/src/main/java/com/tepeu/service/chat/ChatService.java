package com.tepeu.service.chat;

import com.tepeu.agent.tool.FileTools;
import com.tepeu.agent.tool.ShellTools;
import com.tepeu.agent.tool.ToolEventEmittingCallback;
import com.tepeu.agent.tool.ToolEventEmitter;
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
 * <p>This is the Phase-2 Task T1 foundation. It exposes the raw streaming primitive that T2's
 * SSE controller / agent orchestrator will consume. No tool loop, no memory, no session state —
 * those belong to T2's {@code AgentOrchestrator}.
 *
 * <h3>Spring AI 2.0 streaming type</h3>
 * Spring AI 2.0's {@link ChatModel#stream(Prompt)} returns {@code Flux<ChatResponse>} (verified
 * via javap on {@code spring-ai-model-2.0.0.jar}). Each emitted {@link ChatResponse} carries a
 * token chunk in {@code getResult().getOutput().getText()}. Reactor is already on the classpath
 * via the Spring AI starters, so {@code Flux} is available without an extra dependency.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatModelFactory factory;
    /** File tools available to the agent loop; null-safe in case T2b is stripped later. */
    private final FileTools fileTools;
    /** Shell tools：在工作区执行命令（编译/测试/运行） */
    private final ShellTools shellTools;
    /** Jackson 3 mapper (Boot 4 auto-configured bean) for parsing model tool-arg JSON into params. */
    private final ObjectMapper objectMapper;

    public ChatService(ChatModelFactory factory, FileTools fileTools, ShellTools shellTools,
                       ObjectMapper objectMapper) {
        this.factory = factory;
        this.fileTools = fileTools;
        this.shellTools = shellTools;
        this.objectMapper = objectMapper;
    }

    /**
     * Stream a chat completion as {@link ChatResponse} chunks.
     *
     * @param providerId  one of {@code openai}, {@code anthropic}, {@code ollama}
     * @param userMessage the user's prompt text
     * @return a cold {@link Flux} of Spring AI {@link ChatResponse} chunks; the model is resolved
     *         (and credentials validated) lazily when the flux is subscribed, so callers can wire
     *         SSE error handling uniformly
     */
    public Flux<ChatResponse> stream(String providerId, String userMessage) {
        // Build the model inside the operator so credential/validation errors surface as
        // Flux#error (callable onError resume) rather than throwing at assembly time.
        return Flux.defer(() -> {
            ChatModel model = factory.getChatModel(providerId);
            Prompt prompt = new Prompt(new UserMessage(userMessage));
            return model.stream(prompt);
        });
    }

    /**
     * Stream a chat completion for a caller-built {@link Prompt} (e.g. multi-turn history).
     *
     * <p>Additive overload for T2's {@code AgentOrchestrator}, which assembles a {@code Prompt}
     * from persisted conversation history. The single-message {@link #stream(String, String)}
     * overload above is unchanged. Credential/validation errors surface as {@code Flux#error} as
     * with the sibling method.
     */
    public Flux<ChatResponse> stream(String providerId, Prompt prompt) {
        return Flux.defer(() -> {
            ChatModel model = factory.getChatModel(providerId);
            return model.stream(prompt);
        });
    }

    /**
     * Stream a chat completion with the agent's tools registered, letting Spring AI's
     * {@code ChatClient} execute tool calls and loop (Plan&rarr;Tool&rarr;Observe&rarr;Iterate)
     * automatically. Tool events are <b>not</b> surfaced (use the emitter overload for that).
     *
     * <p>Equivalent to {@link #streamWithTools(String, Prompt, ToolEventEmitter)} with a
     * {@link ToolEventEmitter#NOOP no-op emitter}; retained so existing callers/tests that do not
     * care about tool visualization keep working unchanged.
     */
    public Flux<ChatResponse> streamWithTools(String providerId, Prompt prompt) {
        return streamWithTools(providerId, prompt, ToolEventEmitter.NOOP);
    }

    /**
     * Stream a chat completion with the agent's tools registered, letting Spring AI's
     * {@code ChatClient} execute tool calls and loop (Plan&rarr;Tool&rarr;Observe&rarr;Iterate)
     * automatically, <b>and</b> surface each tool invocation as a {@code tool_call}/
     * {@code tool_result} event through {@code emitter}.
     *
     * <p><b>Spring AI 2.0 mechanism (javap-verified on {@code spring-ai-model-2.0.0.jar} and
     * {@code spring-ai-client-chat-2.0.0.jar}):</b> {@link ChatClient#builder(ChatModel)}
     * auto-wires a {@code ToolCallingAdvisor} backed by a {@code DefaultToolCallingManager}. The
     * advisor's {@code adviseStream} runs the full tool loop on the model's behalf; each round the
     * manager resolves the callback and invokes {@link ToolCallback#call(String)} with the model's
     * JSON arg string. To observe those calls we wrap every callback produced by
     * {@link ToolCallbacks#from(Object...)} in a {@link ToolEventEmittingCallback} and register the
     * <i>wrapped</i> array via {@code .defaultToolCallbacks(ToolCallback...)} (builder) and
     * {@code .toolCallbacks(ToolCallback...)} (per-prompt) — both javap-confirmed on
     * {@code ChatClient$Builder} / {@code ChatClient$ChatClientRequestSpec}. Spring AI then invokes
     * our decorator instead of the raw {@code MethodToolCallback}, which emits the before/after
     * events and delegates the real execution.
     *
     * <p><b>Why {@code .chatResponse()} and not {@code .content()}:</b> {@code StreamResponseSpec}
     * exposes three stream shapes — {@code chatClientResponse()} ({@code Flux<ChatClientResponse>}),
     * {@code chatResponse()} ({@code Flux<ChatResponse>}), and {@code content()}
     * ({@code Flux<String>}). We pick {@code chatResponse()} so each emitted chunk still carries
     * {@code getResult().getOutput().getText()}, exactly what {@code ChatController.extractText}
     * already consumes — the SSE {@code {type:token}} contract and the existing tests are untouched.
     * Tool events flow out-of-band through {@code emitter}, interleaved with the token stream by
     * {@code ChatController}'s shared monitor.
     *
     * <p>Credential/validation errors surface as {@code Flux#error} as with the sibling overloads.
     * The two non-tool overloads ({@link #stream(String, String)} and {@link #stream(String, Prompt)})
     * are unchanged, so a no-tool path still works.
     *
     * @param providerId one of {@code openai}, {@code anthropic}, {@code ollama}
     * @param prompt     the caller-assembled prompt (multi-turn history + system message)
     * @param emitter    per-request sink for {@code tool_call}/{@code tool_result} events; pass
     *                   {@link ToolEventEmitter#NOOP} to opt out
     * @return a cold {@link Flux} of Spring AI {@link ChatResponse} chunks; tool-call rounds are
     *         executed internally by the {@code ToolCallingAdvisor} and the final assistant text
     *         streams through as token chunks
     */
    public Flux<ChatResponse> streamWithTools(String providerId, Prompt prompt, ToolEventEmitter emitter) {
        return Flux.defer(() -> {
            ChatModel model = factory.getChatModel(providerId);
            // Register the wrapped tool callbacks exactly once, per-request. Previously these were
            // triple-registered (defaultToolCallbacks + .tools(fileTools) + .toolCallbacks(wrapped)),
            // which would feed the model duplicate tool schemas. The wrapped array already decorates
            // ToolCallbacks.from(fileTools), so .tools() is redundant; and defaultToolCallbacks is
            // deprecated in Spring AI 2.0, so the per-request .toolCallbacks is the supported form.
            // NOTE: the tool loop is only exercised against a real LLM (e2e deferred until a key is
            // provided); correct by inspection per the Spring AI 2.0 ChatClient contract above.
            ToolCallback[] wrapped = ToolEventEmittingCallback.wrapAll(
                    ToolCallbacks.from(fileTools, shellTools), emitter, objectMapper);
            return ChatClient.builder(model).build()
                    .prompt(prompt)
                    .toolCallbacks(wrapped)
                    .stream()
                    .chatResponse();
        });
    }

    /**
     * Probe whether a provider is reachable with its stored credentials (Phase-2 acceptance
     * criterion 1: "fill API key → connection test succeeds"). Resolves the {@link ChatModel}
     * via the factory (which validates config + decrypts the key) and issues one minimal
     * non-streaming call; any failure — bad key (401), unknown provider, missing config, network —
     * collapses to {@code false} so the caller can report a clean pass/fail without leaking
     * internals. The probe makes one real (tiny, billable for cloud providers) round-trip; it is
     * only triggered by an explicit user action (the "Test" button).
     *
     * @return {@code true} if a non-null chat response came back; {@code false} on any failure
     */
    public boolean testConnection(String providerId) {
        try {
            ChatModel model = factory.getChatModel(providerId);
            ChatResponse response = model.call(new Prompt(new UserMessage("ping")));
            return response != null && response.getResult() != null;
        } catch (RuntimeException e) {
            log.warn("Provider {} connection test failed: {}", providerId, e.toString(), e);
            return false;
        }
    }

    /** Test seam / introspection: the tool callbacks that would be registered for a turn. */
    List<ToolCallback> toolCallbacks() {
        return List.of(ToolCallbacks.from(fileTools, shellTools));
    }

    /**
     * Non-streaming helper. Returns the full assistant text for {@code userMessage}. Kept simple:
     * for Phase-2 scale a single blocking call is fine; T2's orchestrator will mostly use
     * {@link #stream}.
     *
     * @return the assistant's reply text
     */
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
