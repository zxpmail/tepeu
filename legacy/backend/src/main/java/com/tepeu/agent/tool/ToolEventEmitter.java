package com.tepeu.agent.tool;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-request sink that observes tool invocations during a single agent turn and forwards them as
 * typed events ({@code tool_call} / {@code tool_result}) to whatever surface the caller wires up
 * (today: an {@link SseEmitter}).
 *
 * <p>This interface decouples the {@link ToolEventEmittingCallback} decorator from the transport.
 * The decorator only knows "emit a Map event"; the concrete emitter decides where it goes. That
 * keeps the decorator unit-testable without an {@link SseEmitter} (see
 * {@code ToolEventEmittingCallbackTest}) and lets future surfaces (WebSocket, log) plug in without
 * touching the tool layer.
 *
 * <p><b>Lifecycle:</b> one emitter per chat request. It is created in {@code ChatController.stream},
 * threaded down through {@code AgentOrchestrator.streamTurn} → {@code ChatService.streamWithTools}
 * → {@code ToolEventEmittingCallback}, and dies with the {@link SseEmitter} it wraps. The static
 * {@link #forSse(SseEmitter, Object, ObjectMapper)} factory captures the SSE instance plus a shared
 * monitor so all sends (token events from the Reactor subscriber and tool events from tool threads)
 * serialize — see the thread-safety note on {@link SseEmitterToolEventEmitter}.
 */
public interface ToolEventEmitter {

    /** No-op emitter used by callers that opt out of tool-event visualization. */
    ToolEventEmitter NOOP = event -> { /* no-op */ };

    /**
     * Forward one typed tool event. Implementations must be safe to call from any thread (the Reactor
     * tool-execution chain may run tool callbacks off the request thread).
     *
     * @param event a flat map; expected to carry a {@code "type"} key of {@code tool_call} or
     *              {@code tool_result} (see {@link ToolEventEmittingCallback} for the exact shapes).
     *              Implementations must not mutate the map.
     */
    void emit(Map<String, Object> event);

    /**
     * Build an emitter that writes each event as an SSE {@code event: message} JSON payload on the
     * given {@link SseEmitter}, serialized under {@code monitor} so concurrent token + tool sends
     * cannot interleave on the wire.
     *
     * @param sse          the SSE connection for this request
     * @param monitor      the shared lock the caller also synchronizes token-event sends on (same
     *                     instance passed to {@code ChatController}'s token path); never null
     * @param objectMapper the controller's Jackson mapper (so event JSON matches the token payload
     *                     style byte-for-byte)
     * @return a request-scoped emitter
     */
    static ToolEventEmitter forSse(SseEmitter sse, Object monitor, ObjectMapper objectMapper) {
        return new SseEmitterToolEventEmitter(sse, monitor, objectMapper);
    }

    /**
     * SSE-backed {@link ToolEventEmitter}. Every {@link #emit(Map)} serializes the payload and calls
     * {@link SseEmitter#send} <i>inside a {@code synchronized(monitor)} block</i>.
     *
     * <p><b>Why the shared monitor:</b> tool calls execute on the Reactor chain, which may run on a
     * different thread than the token subscriber (e.g. a {@code reactor-http-nio} thread during the
     * internal {@code ToolCallingAdvisor} loop vs. the scheduler emitting assistant tokens). Spring's
     * {@link SseEmitter} guards its internal buffer, but two concurrent {@code send} calls can still
     * produce interleaved {@code data:} frames on the wire. {@code ChatController} wraps its token
     * sends in the same {@code monitor}; tool sends acquire it here — so the two streams serialize
     * cleanly regardless of which thread they originate from.
     *
     * <p>Send failures (client disconnect) are swallowed at {@code debug} severity: the SSE lifecycle
     * hooks ({@code onCompletion}/{@code onTimeout}/{@code onError}) in {@code ChatController} will
     * dispose the Reactor subscription and close the stream; we must not throw back into the tool
     * chain (that would abort an otherwise-healthy turn).
     */
    final class SseEmitterToolEventEmitter implements ToolEventEmitter {
        private final SseEmitter sse;
        private final Object monitor;
        private final ObjectMapper objectMapper;

        SseEmitterToolEventEmitter(SseEmitter sse, Object monitor, ObjectMapper objectMapper) {
            this.sse = sse;
            this.monitor = monitor;
            this.objectMapper = objectMapper;
        }

        @Override
        public void emit(Map<String, Object> event) {
            synchronized (monitor) {
                try {
                    String json = objectMapper.writeValueAsString(event);
                    sse.send(SseEmitter.event().name("message").data(json));
                } catch (IOException e) {
                    // Client gone — the SSE lifecycle hooks will tear the subscription down.
                    // Intentionally quiet: a tool event failing to render must not abort the turn.
                } catch (RuntimeException e) {
                    // Serialization failure: log nothing here to avoid pulling a logger into this
                    // small class; the event is dropped but the turn continues.
                }
            }
        }
    }
}
