package com.tepeu.controller;

import com.tepeu.dto.ApiResponse;
import com.tepeu.dto.CreateWorkspaceRequest;
import com.tepeu.model.Workspace;
import com.tepeu.service.TaskService;
import com.tepeu.service.WorkspaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Workspace REST：CRUD、切换校验、累计用量（Spec §3.5）。
 * 关联：WorkspaceService、TaskService。
 */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final TaskService taskService;

    public WorkspaceController(WorkspaceService workspaceService, TaskService taskService) {
        this.workspaceService = workspaceService;
        this.taskService = taskService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Workspace>>> listWorkspaces() {
        return ResponseEntity.ok(ApiResponse.success(workspaceService.listWorkspaces()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Workspace>> getWorkspace(@PathVariable String id) {
        return workspaceService.getWorkspace(id)
                .map(w -> ResponseEntity.ok(ApiResponse.success(w)))
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.error("NOT_FOUND", "工作区不存在：" + id)));
    }

    @PostMapping("/{id}/switch")
    public ResponseEntity<ApiResponse<Workspace>> switchWorkspace(@PathVariable String id) {
        // Phase 1: "current workspace" is client-side state (single-user). This endpoint validates
        // the workspace exists for §5.3.2 contract conformance. Server-side current tracking arrives in Phase 2.
        return workspaceService.getWorkspace(id)
                .map(w -> ResponseEntity.ok(ApiResponse.success("已切换", w)))
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.error("NOT_FOUND", "工作区不存在：" + id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Workspace>> createWorkspace(@RequestBody CreateWorkspaceRequest req) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "工作区名称不能为空"));
        }
        Workspace workspace = workspaceService.createWorkspace(
                req.getName().trim(), req.getDescription(), req.getType(), req.getRootPath());
        return ResponseEntity.ok(ApiResponse.success("工作区已创建", workspace));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Workspace>> updateWorkspace(
            @PathVariable String id, @RequestBody CreateWorkspaceRequest req) {
        return workspaceService.updateWorkspace(id, req.getName(), req.getDescription(), req.getType())
                .map(w -> ResponseEntity.ok(ApiResponse.success("工作区已更新", w)))
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.error("NOT_FOUND", "工作区不存在：" + id)));
    }

    /** 返回工作区累计 token/费用（Spec §3.5 Phase 1 最小视图）。 */
    @GetMapping("/{id}/stats")
    public ResponseEntity<ApiResponse<?>> getStats(@PathVariable String id) {
        if (workspaceService.getWorkspace(id).isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("NOT_FOUND", "工作区不存在：" + id));
        }
        TaskService.WorkspaceStats stats = taskService.getWorkspaceStats(id);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteWorkspace(@PathVariable String id) {
        if (workspaceService.deleteWorkspace(id)) {
            return ResponseEntity.ok(ApiResponse.success("工作区已删除", null));
        }
        return ResponseEntity.status(404)
                .body(ApiResponse.error("NOT_FOUND", "工作区不存在：" + id));
    }
}
