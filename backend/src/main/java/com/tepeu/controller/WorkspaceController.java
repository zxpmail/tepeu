package com.tepeu.controller;

import com.tepeu.dto.ApiResponse;
import com.tepeu.dto.CreateWorkspaceRequest;
import com.tepeu.model.Workspace;
import com.tepeu.service.WorkspaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
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
                        .body(ApiResponse.error("NOT_FOUND", "Workspace not found: " + id)));
    }

    @PostMapping("/{id}/switch")
    public ResponseEntity<ApiResponse<Workspace>> switchWorkspace(@PathVariable String id) {
        // Phase 1: "current workspace" is client-side state (single-user). This endpoint validates
        // the workspace exists for §5.3.2 contract conformance. Server-side current tracking arrives in Phase 2.
        return workspaceService.getWorkspace(id)
                .map(w -> ResponseEntity.ok(ApiResponse.success("Switched", w)))
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.error("NOT_FOUND", "Workspace not found: " + id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Workspace>> createWorkspace(@RequestBody CreateWorkspaceRequest req) {
        if (req.getName() == null || req.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "Workspace name is required"));
        }
        Workspace workspace = workspaceService.createWorkspace(
                req.getName().trim(), req.getDescription(), req.getType(), req.getRootPath());
        return ResponseEntity.ok(ApiResponse.success("Workspace created", workspace));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Workspace>> updateWorkspace(
            @PathVariable String id, @RequestBody CreateWorkspaceRequest req) {
        return workspaceService.updateWorkspace(id, req.getName(), req.getDescription(), req.getType())
                .map(w -> ResponseEntity.ok(ApiResponse.success("Workspace updated", w)))
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.error("NOT_FOUND", "Workspace not found: " + id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteWorkspace(@PathVariable String id) {
        if (workspaceService.deleteWorkspace(id)) {
            return ResponseEntity.ok(ApiResponse.success("Workspace deleted", null));
        }
        return ResponseEntity.status(404)
                .body(ApiResponse.error("NOT_FOUND", "Workspace not found: " + id));
    }
}
