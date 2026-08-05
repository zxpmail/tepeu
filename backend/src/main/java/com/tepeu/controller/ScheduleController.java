package com.tepeu.controller;

import com.tepeu.dto.ApiResponse;
import com.tepeu.dto.ScheduleRequest;
import com.tepeu.model.AgentSchedule;
import com.tepeu.service.ScheduleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * 自主 Agent 定时任务 API（Spec M3.1）。
 * 关联：ScheduleService、ScheduleView。
 */
@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<?>> list(@RequestParam String workspaceId) {
        try {
            List<AgentSchedule> items = scheduleService.list(workspaceId);
            return ResponseEntity.ok(ApiResponse.success(items));
        } catch (IllegalArgumentException e) {
            return notFoundOrBad(e);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> get(@PathVariable String id) {
        Optional<AgentSchedule> s = scheduleService.get(id);
        if (s.isEmpty()) {
            return ResponseEntity.status(404).body(ApiResponse.error("NOT_FOUND", "定时任务不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(s.get()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<?>> create(@RequestBody ScheduleRequest req) {
        if (req == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "请求体不能为空"));
        }
        try {
            boolean enabled = req.getEnabled() == null || req.getEnabled();
            int interval = req.getIntervalMinutes() == null ? 60 : req.getIntervalMinutes();
            AgentSchedule s = scheduleService.create(
                    req.getWorkspaceId(),
                    req.getName(),
                    req.getPrompt(),
                    req.getProviderId(),
                    interval,
                    enabled);
            return ResponseEntity.ok(ApiResponse.success("定时任务已创建", s));
        } catch (IllegalArgumentException e) {
            return notFoundOrBad(e);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> update(@PathVariable String id, @RequestBody ScheduleRequest req) {
        if (req == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "请求体不能为空"));
        }
        try {
            Optional<AgentSchedule> updated = scheduleService.update(
                    id,
                    req.getName(),
                    req.getPrompt(),
                    req.getProviderId(),
                    req.getIntervalMinutes(),
                    req.getEnabled());
            if (updated.isEmpty()) {
                return ResponseEntity.status(404).body(ApiResponse.error("NOT_FOUND", "定时任务不存在"));
            }
            return ResponseEntity.ok(ApiResponse.success("定时任务已更新", updated.get()));
        } catch (IllegalArgumentException e) {
            return notFoundOrBad(e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> delete(@PathVariable String id) {
        if (!scheduleService.delete(id)) {
            return ResponseEntity.status(404).body(ApiResponse.error("NOT_FOUND", "定时任务不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success("定时任务已删除", null));
    }

    @PostMapping("/{id}/run")
    public ResponseEntity<ApiResponse<?>> runNow(@PathVariable String id) {
        try {
            AgentSchedule s = scheduleService.runNow(id);
            return ResponseEntity.ok(ApiResponse.success("已开始运行", s));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(ApiResponse.error("ALREADY_RUNNING", e.getMessage()));
        }
    }

    private static ResponseEntity<ApiResponse<?>> notFoundOrBad(IllegalArgumentException e) {
        String msg = e.getMessage() == null ? "无效请求" : e.getMessage();
        if (msg.startsWith("工作区不存在") || msg.startsWith("定时任务不存在")
                || msg.startsWith("Workspace not found") || msg.startsWith("Schedule not found")) {
            return ResponseEntity.status(404).body(ApiResponse.error("NOT_FOUND", msg));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", msg));
    }
}
