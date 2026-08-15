package com.tepeu.controller;

import com.tepeu.dto.ApiResponse;
import com.tepeu.dto.CreateSessionRequest;
import com.tepeu.dto.ForkSessionRequest;
import com.tepeu.dto.UpdateSessionRequest;
import com.tepeu.model.Session;
import com.tepeu.service.SessionService;
import com.tepeu.service.TaskService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST CRUD for chat sessions and their message history.
 *
 * <ul>
 *   <li>{@code GET /api/session?workspaceId=} — list sessions for a workspace</li>
 *   <li>{@code POST /api/session} — create a session</li>
 *   <li>{@code GET /api/session/{id}} — session + its messages</li>
 *   <li>{@code DELETE /api/session/{id}} — delete session (messages cascade)</li>
 *   <li>{@code PATCH /api/session/{id}} — rename session title</li>
 *   <li>{@code POST /api/session/{id}/fork} — fork from a message</li>
 *   <li>{@code GET /api/session/{id}/stats} — token/cost/message stats</li>
 * </ul>
 *
 * <p>The streaming chat endpoint lives at {@code ChatController} ({@code POST /api/chat/stream});
 * this controller only manages session lifecycle and history retrieval.
 */
@RestController
@RequestMapping("/api/session")
public class SessionController {

    private final SessionService sessionService;
    private final TaskService taskService;

    public SessionController(SessionService sessionService, TaskService taskService) {
        this.sessionService = sessionService;
        this.taskService = taskService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<?>> listSessions(@RequestParam(required = false) String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "workspaceId is required"));
        }
        List<Session> sessions = sessionService.listSessions(workspaceId);
        return ResponseEntity.ok(ApiResponse.success(sessions));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Session>> createSession(@RequestBody CreateSessionRequest req) {
        if (req.getWorkspaceId() == null || req.getWorkspaceId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "workspaceId is required"));
        }
        Session session = sessionService.createSession(req.getWorkspaceId(), req.getTitle());
        return ResponseEntity.ok(ApiResponse.success("Session created", session));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> getSession(@PathVariable String id) {
        return sessionService.getSessionWithMessages(id)
                .<ResponseEntity<ApiResponse<?>>>map(view -> ResponseEntity.ok(ApiResponse.success(view)))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(ApiResponse.error("NOT_FOUND", "Session not found: " + id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteSession(@PathVariable String id) {
        if (sessionService.deleteSession(id)) {
            return ResponseEntity.ok(ApiResponse.success("Session deleted", null));
        }
        return ResponseEntity.status(404)
                .body(ApiResponse.error("NOT_FOUND", "Session not found: " + id));
    }

    /** 重命名会话。 */
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<?>> renameSession(
            @PathVariable String id, @RequestBody UpdateSessionRequest req) {
        if (req == null || req.getTitle() == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "title is required"));
        }
        return sessionService.renameSession(id, req.getTitle())
                .<ResponseEntity<ApiResponse<?>>>map(s ->
                        ResponseEntity.ok(ApiResponse.success("Session renamed", s)))
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(ApiResponse.error("NOT_FOUND", "Session not found: " + id)));
    }

    /** 从指定消息处分叉出新会话。 */
    @PostMapping("/{id}/fork")
    public ResponseEntity<ApiResponse<?>> forkSession(
            @PathVariable String id, @RequestBody ForkSessionRequest req) {
        if (req == null || req.getMessageId() == null || req.getMessageId().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "messageId is required"));
        }
        try {
            SessionService.SessionWithMessages forked =
                    sessionService.forkFromMessage(id, req.getMessageId());
            return ResponseEntity.ok(ApiResponse.success("Session forked", forked));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        }
    }

    /** 返回会话 token/费用/消息统计。 */
    @GetMapping("/{id}/stats")
    public ResponseEntity<ApiResponse<?>> getStats(@PathVariable String id) {
        if (sessionService.getSession(id).isEmpty()) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("NOT_FOUND", "Session not found: " + id));
        }
        TaskService.SessionStats stats = taskService.getSessionStats(id);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }
}
