package com.tepeu.agent.tool;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused unit tests for {@link ToolEventEmittingCallback}. No Spring context, no LLM. Covers:
 * <ul>
 *   <li>{@code tool_call} fires <b>before</b> the delegate runs and {@code tool_result} fires
 *       <b>after</b> (ordering asserted via a capturing emitter + a delegate that records call time).</li>
 *   <li>The exact JSON-shape payloads ({@code type/tool/params}, {@code type/tool/content}).</li>
 *   <li>{@code params} is the parsed model-arg JSON map; malformed args collapse to an empty map but
 *       the {@code tool_call} event still fires.</li>
 *   <li>{@code content} is truncated to {@link ToolEventEmittingCallback#MAX_RESULT_CHARS}.</li>
 *   <li>The delegate's return value is passed through unchanged; a delegate exception propagates and
 *       <b>no</b> {@code tool_result} is emitted.</li>
 *   <li>{@link ToolCallback} metadata ({@code getToolDefinition}/{@code getToolMetadata}) delegate
 *       through unchanged.</li>
 *   <li>{@link ToolEventEmittingCallback#wrapAll} wraps every element and preserves order/length.</li>
 * </ul>
 */
class ToolEventEmittingCallbackTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Minimal {@link ToolCallback} stub: returns fixed result, records call count, optionally throws. */
    private static final class StubCallback implements ToolCallback {
        final String name;
        final String result;
        final RuntimeException toThrow;
        int callCount;

        StubCallback(String name, String result) {
            this(name, result, null);
        }

        StubCallback(String name, String result, RuntimeException toThrow) {
            this.name = name;
            this.result = result;
            this.toThrow = toThrow;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return new ToolDefinition() {
                @Override public String name() { return name; }
                @Override public String description() { return "stub"; }
                @Override public String inputSchema() { return "{}"; }
            };
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return ToolMetadata.builder().build();
        }

        @Override
        public String call(String toolInput) {
            callCount++;
            if (toThrow != null) {
                throw toThrow;
            }
            return result;
        }
    }

    /** Captures every emitted event in order, for assertions. */
    private static final class CapturingEmitter implements ToolEventEmitter {
        final List<Map<String, Object>> events = new ArrayList<>();

        @Override
        public void emit(Map<String, Object> event) {
            events.add(Map.copyOf(event));
        }
    }

    @Test
    void call_emitsToolCallBeforeAndToolResultAfter() {
        StubCallback delegate = new StubCallback("list_files", "[FILE] a.txt");
        CapturingEmitter emitter = new CapturingEmitter();
        ToolCallback wrapped = new ToolEventEmittingCallback(delegate, emitter, mapper);

        String out = wrapped.call("{\"path\":\"/\"}");

        assertEquals("[FILE] a.txt", out, "delegate return value passes through unchanged");
        assertEquals(1, delegate.callCount, "delegate executed exactly once");
        assertEquals(2, emitter.events.size(), "exactly two events (tool_call + tool_result)");

        Map<String, Object> callEvent = emitter.events.get(0);
        assertEquals("tool_call", callEvent.get("type"));
        assertEquals("list_files", callEvent.get("tool"));
        assertEquals(Map.of("path", "/"), callEvent.get("params"));

        Map<String, Object> resultEvent = emitter.events.get(1);
        assertEquals("tool_result", resultEvent.get("type"));
        assertEquals("list_files", resultEvent.get("tool"));
        assertEquals("[FILE] a.txt", resultEvent.get("content"));
    }

    @Test
    void callWithToolContext_alsoEmitsBothEventsAndDelegates() {
        StubCallback delegate = new StubCallback("read_file", "hello");
        CapturingEmitter emitter = new CapturingEmitter();
        ToolCallback wrapped = new ToolEventEmittingCallback(delegate, emitter, mapper);

        String out = wrapped.call("{\"path\":\"/a.txt\"}", new ToolContext(Map.of()));

        assertEquals("hello", out);
        assertEquals(1, delegate.callCount);
        assertEquals(2, emitter.events.size());
        assertEquals("tool_call", emitter.events.get(0).get("type"));
        assertEquals("tool_result", emitter.events.get(1).get("type"));
    }

    @Test
    void malformedArgsParams_fallsBackToEmptyMapButStillEmitsToolCall() {
        StubCallback delegate = new StubCallback("list_files", "ok");
        CapturingEmitter emitter = new CapturingEmitter();
        ToolCallback wrapped = new ToolEventEmittingCallback(delegate, emitter, mapper);

        wrapped.call("not valid json {{{");

        assertEquals(2, emitter.events.size(), "both events still fire on malformed args");
        Map<String, Object> callEvent = emitter.events.get(0);
        assertEquals("tool_call", callEvent.get("type"));
        assertEquals(Map.of(), callEvent.get("params"), "params collapse to {} on parse failure");
    }

    @Test
    void blankArgsParams_yieldsEmptyMap() {
        StubCallback delegate = new StubCallback("list_files", "ok");
        CapturingEmitter emitter = new CapturingEmitter();
        ToolCallback wrapped = new ToolEventEmittingCallback(delegate, emitter, mapper);

        wrapped.call("   ");

        assertEquals(Map.of(), emitter.events.get(0).get("params"));
    }

    @Test
    void largeResultContent_isTruncatedToOneKilobyte() {
        String big = "x".repeat(ToolEventEmittingCallback.MAX_RESULT_CHARS * 3);
        StubCallback delegate = new StubCallback("read_file", big);
        CapturingEmitter emitter = new CapturingEmitter();
        ToolCallback wrapped = new ToolEventEmittingCallback(delegate, emitter, mapper);

        wrapped.call("{}");

        Map<String, Object> resultEvent = emitter.events.get(1);
        String content = (String) resultEvent.get("content");
        assertEquals(ToolEventEmittingCallback.MAX_RESULT_CHARS, content.length(),
                "emitted content capped; the model still receives the full result internally");
        assertTrue(content.matches("x+"), "truncated content is the head of the original");
    }

    @Test
    void nullResultContent_becomesEmptyString() {
        StubCallback delegate = new StubCallback("list_files", null);
        CapturingEmitter emitter = new CapturingEmitter();
        ToolCallback wrapped = new ToolEventEmittingCallback(delegate, emitter, mapper);

        wrapped.call("{}");

        assertEquals("", emitter.events.get(1).get("content"));
    }

    @Test
    void delegateThrows_propagatesAndDoesNotEmitToolResult() {
        RuntimeException boom = new RuntimeException("tool blew up");
        StubCallback delegate = new StubCallback("read_file", "unused", boom);
        CapturingEmitter emitter = new CapturingEmitter();
        ToolCallback wrapped = new ToolEventEmittingCallback(delegate, emitter, mapper);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> wrapped.call("{}"));
        assertSame(boom, thrown, "the original exception is re-thrown unchanged");
        assertEquals(1, emitter.events.size(), "only tool_call emitted; no synthetic tool_result");
        assertEquals("tool_call", emitter.events.get(0).get("type"));
    }

    @Test
    void metadataDelegatesThroughUnchanged() {
        StubCallback delegate = new StubCallback("read_file", "ok");
        ToolCallback wrapped = new ToolEventEmittingCallback(delegate, ToolEventEmitter.NOOP, mapper);

        assertEquals("read_file", wrapped.getToolDefinition().name());
        assertEquals("stub", wrapped.getToolDefinition().description());
        assertEquals("{}", wrapped.getToolDefinition().inputSchema());
        assertNotNull(wrapped.getToolMetadata());
    }

    @Test
    void wrapAll_wrapsEveryElementAndPreservesOrder() {
        StubCallback a = new StubCallback("list_files", "a");
        StubCallback b = new StubCallback("read_file", "b");
        CapturingEmitter emitter = new CapturingEmitter();

        ToolCallback[] wrapped = ToolEventEmittingCallback.wrapAll(new ToolCallback[]{a, b}, emitter, mapper);

        assertEquals(2, wrapped.length);
        assertInstanceOf(ToolEventEmittingCallback.class, wrapped[0]);
        assertInstanceOf(ToolEventEmittingCallback.class, wrapped[1]);
        assertEquals("list_files", wrapped[0].getToolDefinition().name());
        assertEquals("read_file", wrapped[1].getToolDefinition().name());

        // Driving the first wrapped callback routes through the decorator to delegate `a`.
        wrapped[0].call("{}");
        assertEquals(1, a.callCount);
        assertEquals(0, b.callCount);
        assertEquals(2, emitter.events.size());
    }
}
