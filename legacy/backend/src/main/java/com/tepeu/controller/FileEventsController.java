package com.tepeu.controller;

import com.tepeu.service.FileWatcherService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 全局文件变更事件流：{@code GET /api/events} → 常驻 {@link SseEmitter}。
 * <p>Phase 12：前端 {@code EventSource('/api/events')} 保持连接，收到 {@code file_changed}
 * 事件（含 workspaceId）后经前端事件总线按当前工作区过滤刷新文件列表。GET 只读，无需实例令牌。
 * <p>关联：FileWatcherService、WorkspaceEvents.tsx（前端事件源）。
 */
@RestController
@RequestMapping("/api/events")
public class FileEventsController {

    private final FileWatcherService fileWatcherService;

    public FileEventsController(FileWatcherService fileWatcherService) {
        this.fileWatcherService = fileWatcherService;
    }

    @GetMapping
    public SseEmitter stream() {
        // 0L = 永不过期，常驻通道；断线由前端 EventSource 自动重连
        SseEmitter emitter = new SseEmitter(0L);
        fileWatcherService.subscribe(emitter);
        return emitter;
    }
}
