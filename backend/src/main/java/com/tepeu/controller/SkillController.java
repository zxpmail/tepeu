package com.tepeu.controller;

import com.tepeu.dto.ApiResponse;
import com.tepeu.dto.InstallSkillRequest;
import com.tepeu.dto.UpdateSkillRequest;
import com.tepeu.model.Skill;
import com.tepeu.service.SkillService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 工作区技能 API — 列表 / URL·ZIP·粘贴安装 / 启用 / 卸载。
 * 关联：SkillService、SkillsView。
 */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<?>> list(@RequestParam String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "workspaceId is required"));
        }
        List<Skill> skills = skillService.listSkills(workspaceId);
        return ResponseEntity.ok(ApiResponse.success(skills));
    }

    /**
     * 一键安装 ReqForge 编程套件（GitHub raw）。
     * POST /api/skills/packs/reqforge-coding?workspaceId=
     */
    @PostMapping("/packs/reqforge-coding")
    public ResponseEntity<ApiResponse<?>> installReqForgeCodingPack(@RequestParam String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "workspaceId is required"));
        }
        try {
            SkillService.PackResult result = skillService.installReqForgeCodingPack(workspaceId);
            java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("installed", result.installed());
            data.put("failed", result.failed());
            data.put("errors", result.errors());
            data.put("skills", result.skills());
            String msg = result.failed() == 0
                    ? "ReqForge 编程套件已安装"
                    : "部分安装成功（失败 " + result.failed() + " 项）";
            return ResponseEntity.ok(ApiResponse.success(msg, data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", e.getMessage()));
        }
    }

    /**
     * JSON 安装：提供 {@code url} 或 {@code content} 之一。
     */
    @PostMapping
    public ResponseEntity<ApiResponse<?>> install(@RequestBody InstallSkillRequest req) {
        if (req.getWorkspaceId() == null || req.getWorkspaceId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "workspaceId is required"));
        }
        boolean hasUrl = req.getUrl() != null && !req.getUrl().isBlank();
        boolean hasContent = req.getContent() != null && !req.getContent().isBlank();
        if (!hasUrl && !hasContent) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "url or content is required"));
        }
        try {
            Skill skill;
            if (hasUrl) {
                skill = skillService.installFromUrl(req.getWorkspaceId(), req.getName(), req.getUrl());
            } else {
                skill = skillService.install(req.getWorkspaceId(), req.getName(), req.getContent());
            }
            return ResponseEntity.ok(ApiResponse.success("Skill installed", skill));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", e.getMessage()));
        }
    }

    /**
     * 上传 ZIP 技能包（需含 SKILL.md）。
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<?>> uploadZip(
            @RequestParam String workspaceId,
            @RequestParam(required = false) String name,
            @RequestParam("file") MultipartFile file) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "workspaceId is required"));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "file is required"));
        }
        try {
            Skill skill = skillService.installFromZip(workspaceId, name, file.getBytes());
            return ResponseEntity.ok(ApiResponse.success("Skill installed", skill));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("IO_ERROR", "Failed to read upload: " + e.getMessage()));
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> setEnabled(
            @PathVariable String id, @RequestBody UpdateSkillRequest req) {
        if (req.getEnabled() == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "enabled is required"));
        }
        var updated = skillService.setEnabled(id, req.getEnabled());
        if (updated.isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("NOT_FOUND", "Skill not found: " + id));
        }
        return ResponseEntity.ok(ApiResponse.success("Skill updated", updated.get()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        try {
            if (skillService.delete(id)) {
                return ResponseEntity.ok(ApiResponse.success("Skill deleted", null));
            }
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("NOT_FOUND", "Skill not found: " + id));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("FORBIDDEN", e.getMessage()));
        }
    }
}
