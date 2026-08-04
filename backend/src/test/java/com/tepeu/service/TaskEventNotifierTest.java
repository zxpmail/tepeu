package com.tepeu.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 后台任务事件通知广播（Phase 13）。 */
class TaskEventNotifierTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void publish_broadcastsToAllListeners() {
        TaskEventNotifier notifier = new TaskEventNotifier(objectMapper);
        List<Map<String, Object>> received = new ArrayList<>();
        notifier.addListener(received::add);
        notifier.addListener(received::add);

        Map<String, Object> payload = Map.of(
                "type", "task_completed",
                "scheduleName", "日报",
                "workspaceId", "ws-1");
        notifier.publish(payload);

        assertEquals(2, received.size(), "两个订阅者都应收到同一条事件");
        assertEquals("task_completed", received.get(0).get("type"));
        assertEquals("日报", received.get(0).get("scheduleName"));
    }

    @Test
    void publish_ignoresBrokenListener() {
        TaskEventNotifier notifier = new TaskEventNotifier(objectMapper);
        notifier.addListener(payload -> {
            throw new IllegalStateException("boom");
        });
        List<Map<String, Object>> received = new ArrayList<>();
        notifier.addListener(received::add);

        notifier.publish(Map.of("type", "task_failed"));

        assertEquals(1, received.size(), "异常订阅者不应阻断其他订阅者");
    }

    @Test
    void publish_withNoListeners_isNoOp() {
        TaskEventNotifier notifier = new TaskEventNotifier(objectMapper);
        // 不应抛异常
        assertDoesNotThrow(() -> notifier.publish(Map.of("type", "task_completed")));
    }
}
