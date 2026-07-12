package com.tepeu.agent.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Decorates a {@link ToolCallback} to emit {@code tool_call} (before) and {@code tool_result}
 * (after) events through a {@link ToolEventEmitter}, without altering the delegate's behavior.
 *
 * <p><b>Spring AI 2.0 interception point (javap-verified):</b> the auto-wired
 * {@code ToolCallingAdvisor} + {@code DefaultToolCallingManager} resolve each registered callback
 * and invoke {@link ToolCallback#call(String)} (or {@link ToolCallback#call(String, ToolContext)})
 * with the model's JSON argument string. {@code MethodToolCallback} is {@code final}, so we
 * decorate the {@link ToolCallback} <i>interface</i>: every method delegates to the wrapped
 * instance, and the two {@code call(...)} overloads bracket the delegate call with the emit
 * before/after. The decorator is registered in place of the raw callback (via
 * {@code ChatClient.Builder.defaultToolCallbacks(ToolCallback...)} and
 * {@code ChatClientRequestSpec.toolCallbacks(ToolCallback...)}, both javap-confirmed on
 * {@code spring-ai-client-chat-2.0.0.jar}), so Spring AI invokes us instead of the original.
 *
 * <h3>Emitted event shapes</h3>
 * <pre>
 *   tool_call  : {"type":"tool_call", "tool":"&lt;name&gt;", "params":&lt;parsed args Map; {} on parse failure&gt;}
 *   tool_result: {"type":"tool_result","tool":"&lt;name&gt;", "content":"&lt;output, truncated to ~1KB&gt;"}
 * </pre>
 * {@code params} is the JSON-arg string parsed into a {@code Map<String,Object>}; if the model sent
 * malformed JSON we fall back to an empty map rather than dropping the {@code tool_call} event (the
 * frontend still learns the tool was invoked). {@code content} is the delegate's raw return string,
 * truncated at {@link #MAX_RESULT_CHARS} so a huge file read does not flood the SSE channel (the
 * model still receives the full result internally; only the visualized copy is capped).
 *
 * <h3>Failure handling</h3>
 * If the delegate throws, we do <b>not</b> emit a synthetic {@code tool_result} (the real result is
 * an exception, which Spring AI's {@code ToolExecutionExceptionProcessor} handles). We re-throw the
 * original exception unchanged so the framework's error path stays intact.
 *
 * <p>Thread-safe: the decorator holds no mutable state; all thread-safety lives in the
 * {@link ToolEventEmitter} implementation.
 */
public final class ToolEventEmittingCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(ToolEventEmittingCallback.class);

    /** Cap on the {@code tool_result.content} sent to the client (~1KB). The model sees the full result. */
    static final int MAX_RESULT_CHARS = 1024;

    private final ToolCallback delegate;
    private final ToolEventEmitter emitter;
    private final ObjectMapper objectMapper;

    /**
     * @param delegate     the real callback produced by {@code ToolCallbacks.from(Object...)}
     * @param emitter      per-request sink; never null (use {@link ToolEventEmitter#NOOP} to disable)
     * @param objectMapper Jackson mapper used to parse the model's JSON arg string into a {@code params} Map
     */
    public ToolEventEmittingCallback(ToolCallback delegate, ToolEventEmitter emitter, ObjectMapper objectMapper) {
        this.delegate = delegate;
        this.emitter = emitter;
        this.objectMapper = objectMapper;
    }

    /** Wrap each callback in {@code callbacks} with a decorator sharing {@code emitter}. */
    public static ToolCallback[] wrapAll(ToolCallback[] callbacks, ToolEventEmitter emitter, ObjectMapper objectMapper) {
        ToolCallback[] wrapped = new ToolCallback[callbacks.length];
        for (int i = 0; i < callbacks.length; i++) {
            wrapped[i] = new ToolEventEmittingCallback(callbacks[i], emitter, objectMapper);
        }
        return wrapped;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
        String name = delegate.getToolDefinition().name();
        emitter.emit(toolCallEvent(name, toolInput));
        String result = delegate.call(toolInput);
        emitter.emit(toolResultEvent(name, result));
        maybeEmitFileChanged(name, toolInput, result);
        return result;
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        String name = delegate.getToolDefinition().name();
        emitter.emit(toolCallEvent(name, toolInput));
        String result = delegate.call(toolInput, toolContext);
        emitter.emit(toolResultEvent(name, result));
        maybeEmitFileChanged(name, toolInput, result);
        return result;
    }

    /** write_file / run_command 成功后额外发 file_changed，供前端刷新文件树 */
    private void maybeEmitFileChanged(String name, String toolInput, String result) {
        if (result == null || result.startsWith("ERROR:")) return;
        if ("write_file".equals(name)) {
            if (!result.startsWith("OK:")) return;
            Map<?, ?> params = parseParams(toolInput);
            Object path = params.get("path");
            if (path == null || path.toString().isBlank()) return;
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "file_changed");
            event.put("path", path.toString());
            event.put("operation", "write");
            emitter.emit(event);
            return;
        }
        if ("run_command".equals(name)) {
            // 命令可能生成/修改文件：通知文件树刷新（path 空串 = 整树刷新）
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "file_changed");
            event.put("path", "");
            event.put("operation", "command");
            emitter.emit(event);
        }
    }

    private Map<String, Object> toolCallEvent(String name, String toolInput) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "tool_call");
        event.put("tool", name);
        event.put("params", parseParams(toolInput));
        return event;
    }

    private Map<String, Object> toolResultEvent(String name, String result) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", "tool_result");
        event.put("tool", name);
        event.put("content", truncate(result));
        return event;
    }

    /** Parse the model's JSON arg string; on failure return an empty map (event still fires). */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseParams(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(toolInput, Map.class);
        } catch (RuntimeException e) {
            // Jackson 3 throws RuntimeException (not checked). Malformed model args: keep the event,
            // drop the params so the frontend still sees the tool was called.
            log.debug("Could not parse tool args for '{}' as JSON: {}", name(), e.getMessage());
            return Map.of();
        }
    }

    private static String truncate(String result) {
        if (result == null) {
            return "";
        }
        return result.length() <= MAX_RESULT_CHARS ? result : result.substring(0, MAX_RESULT_CHARS);
    }

    private String name() {
        return delegate.getToolDefinition().name();
    }
}
