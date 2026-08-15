package com.tepeu.controller;

import com.tepeu.service.TaskEventNotifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 后台任务事件流：{@code GET /api/task-events} → 常驻 {@link SseEmitter}。
 * <p>Phase 13：前端 {@code EventSource('/api/task-events')} 保持连接，收到 {@code task_completed} /
 * {@code task_failed} 后经通知 store 弹出通知栏 badge / 浏览器 Notification。GET 只读，无需实例令牌。
 * <p>关联：TaskEventNotifier、ScheduleService、useNotifications.ts（前端事件源）。
 */
@RestController
@RequestMapping("/api/task-events")
public class TaskEventController {

    private final TaskEventNotifier taskEventNotifier;

    public TaskEventController(TaskEventNotifier taskEventNotifier) {
        this.taskEventNotifier = taskEventNotifier;
    }

    @GetMapping
    public SseEmitter stream() {
        // 0L = 永不过期，常驻通道；断线由前端 EventSource 自动重连
        SseEmitter emitter = new SseEmitter(0L);
        taskEventNotifier.subscribe(emitter);
        return emitter;
    }
}
