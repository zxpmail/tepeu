package com.tepeu.controller;

import com.tepeu.dto.ApiResponse;
import com.tepeu.dto.CreateMemoryRequest;
import com.tepeu.dto.MemorySearchRequest;
import com.tepeu.dto.UpdateMemoryRequest;
import com.tepeu.model.Memory;
import com.tepeu.service.MemoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/memory")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    /** 列表：无关键词时按时间倒序；有 query/tags 时走搜索（FTS/LIKE） */
    @GetMapping
    public ResponseEntity<ApiResponse<?>> listMemories(
            @RequestParam String workspaceId,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String cursor) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "需要指定工作区"));
        }
        int pageSize = limit != null ? limit : 20;
        boolean searching = (query != null && !query.isBlank())
                || (tags != null && !tags.isEmpty());
        List<Memory> results = searching
                ? memoryService.searchMemories(workspaceId, query, tags, pageSize + 1, cursor)
                : memoryService.listMemories(workspaceId, pageSize + 1, cursor);
        return ResponseEntity.ok(ApiResponse.success(pageResult(results, pageSize)));
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<?>> searchMemories(@RequestBody MemorySearchRequest req) {
        if (req.getWorkspaceId() == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "需要指定工作区"));
        }
        int pageSize = req.getLimit() != null ? req.getLimit() : 20;
        // 多取一行判断 hasMore，避免幽灵下一页
        List<Memory> results = memoryService.searchMemories(
                req.getWorkspaceId(), req.getQuery(), req.getTags(), pageSize + 1, req.getCursor());
        return ResponseEntity.ok(ApiResponse.success(pageResult(results, pageSize)));
    }

    private static Map<String, Object> pageResult(List<Memory> results, int pageSize) {
        boolean hasMore = results.size() > pageSize;
        List<Memory> page = hasMore ? results.subList(0, pageSize) : results;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", page);
        data.put("hasMore", hasMore);
        if (hasMore && !page.isEmpty()) {
            data.put("nextCursor", page.get(page.size() - 1).getCreatedAt().toString());
        }
        return data;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Memory>> createMemory(@RequestBody CreateMemoryRequest req) {
        if (req.getWorkspaceId() == null || req.getContent() == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "需要工作区与记忆内容"));
        }
        String source = req.getSource() != null ? req.getSource() : "manual";
        List<String> tags = req.getTags() != null ? req.getTags() : List.of();
        Memory memory = memoryService.createMemory(req.getWorkspaceId(), source, req.getContent(), tags);
        return ResponseEntity.ok(ApiResponse.success("记忆已创建", memory));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Memory>> getMemory(@PathVariable String id) {
        return memoryService.getMemory(id)
                .map(m -> ResponseEntity.ok(ApiResponse.success(m)))
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.error("NOT_FOUND", "记忆不存在：" + id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Memory>> updateMemory(
            @PathVariable String id, @RequestBody UpdateMemoryRequest req) {
        return memoryService.updateMemory(id, req.getContent(), req.getTags())
                .map(m -> ResponseEntity.ok(ApiResponse.success("记忆已更新", m)))
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.error("NOT_FOUND", "记忆不存在：" + id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMemory(@PathVariable String id) {
        if (memoryService.deleteMemory(id)) {
            return ResponseEntity.ok(ApiResponse.success("记忆已删除", null));
        }
        return ResponseEntity.status(404)
                .body(ApiResponse.error("NOT_FOUND", "记忆不存在：" + id));
    }
}
