package com.tepeu.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 后台任务事件通知（Phase 13，Spec M3.1 补齐体验）。
 * <p>自主调度任务完成后经 {@code GET /api/task-events} 常驻 SSE 推 {@code task_completed} /
 * {@code task_failed}，前端通知栏 badge + 可选浏览器 Notification 即时提示。
 * <p>事件形状：{@code {type, scheduleId, scheduleName, workspaceId, sessionId?, message}}。
 * 与 FileWatcherService 同模式（subscribe/sendJson/broadcast）；任务事件低频，每 tab 直连即可，
 * 无需多 tab leader 选举（见 ADR-013）。关联：TaskEventController、ScheduleService、useNotifications。
 */
@Component
public class TaskEventNotifier {

    private static final Logger log = LoggerFactory.getLogger(TaskEventNotifier.class);

    private final ObjectMapper objectMapper;
    private final CopyOnWriteArrayList<Consumer<Map<String, Object>>> listeners = new CopyOnWriteArrayList<>();

    public TaskEventNotifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 注册一个 SSE 订阅；连接完成/超时/出错时自动移除。关联：TaskEventController。 */
    public void subscribe(SseEmitter emitter) {
        Consumer<Map<String, Object>> listener = payload -> sendJson(emitter, payload);
        listeners.add(listener);
        emitter.onCompletion(() -> listeners.remove(listener));
        emitter.onTimeout(() -> listeners.remove(listener));
        emitter.onError(t -> listeners.remove(listener));
    }

    /** 广播一条任务事件给所有订阅者。 */
    public void publish(Map<String, Object> payload) {
        for (Consumer<Map<String, Object>> listener : listeners) {
            try {
                listener.accept(payload);
            } catch (RuntimeException e) {
                log.debug("广播异常被忽略: {}", e.getMessage());
            }
        }
    }

    private void sendJson(SseEmitter emitter, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            emitter.send(SseEmitter.event().name("message").data(json));
        } catch (IOException | RuntimeException e) {
            log.debug("SSE 推送失败（客户端断开?）: {}", e.getMessage());
        }
    }

    /** 测试钩子（包内可见，供 TaskEventNotifierTest 断言广播）。 */
    void addListener(Consumer<Map<String, Object>> listener) {
        listeners.add(listener);
    }
}
