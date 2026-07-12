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

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<?>> searchMemories(@RequestBody MemorySearchRequest req) {
        if (req.getWorkspaceId() == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "workspaceId is required"));
        }
        int limit = req.getLimit() != null ? req.getLimit() : 20;
        // Fetch one extra row to compute hasMore without a phantom next page.
        List<Memory> results = memoryService.searchMemories(
                req.getWorkspaceId(), req.getQuery(), req.getTags(), limit + 1, req.getCursor());
        boolean hasMore = results.size() > limit;
        List<Memory> page = hasMore ? results.subList(0, limit) : results;

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", page);
        data.put("hasMore", hasMore);
        if (hasMore && !page.isEmpty()) {
            data.put("nextCursor", page.get(page.size() - 1).getCreatedAt().toString());
        }
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Memory>> createMemory(@RequestBody CreateMemoryRequest req) {
        if (req.getWorkspaceId() == null || req.getContent() == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "workspaceId and content are required"));
        }
        String source = req.getSource() != null ? req.getSource() : "manual";
        List<String> tags = req.getTags() != null ? req.getTags() : List.of();
        Memory memory = memoryService.createMemory(req.getWorkspaceId(), source, req.getContent(), tags);
        return ResponseEntity.ok(ApiResponse.success("Memory created", memory));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Memory>> getMemory(@PathVariable String id) {
        return memoryService.getMemory(id)
                .map(m -> ResponseEntity.ok(ApiResponse.success(m)))
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.error("NOT_FOUND", "Memory not found: " + id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Memory>> updateMemory(
            @PathVariable String id, @RequestBody UpdateMemoryRequest req) {
        return memoryService.updateMemory(id, req.getContent(), req.getTags())
                .map(m -> ResponseEntity.ok(ApiResponse.success("Memory updated", m)))
                .orElse(ResponseEntity.status(404)
                        .body(ApiResponse.error("NOT_FOUND", "Memory not found: " + id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMemory(@PathVariable String id) {
        if (memoryService.deleteMemory(id)) {
            return ResponseEntity.ok(ApiResponse.success("Memory deleted", null));
        }
        return ResponseEntity.status(404)
                .body(ApiResponse.error("NOT_FOUND", "Memory not found: " + id));
    }
}
