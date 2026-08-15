package com.tepeu.controller;

import com.tepeu.agent.hook.ApprovalStore;
import com.tepeu.dto.ApiResponse;
import com.tepeu.dto.ApprovalDecisionRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 工具审批 REST：用户 Approve/Deny 高危工具调用（Spec M2.3）。
 * 关联：ApprovalStore、HookingToolCallback、前端 ApprovalBanner。
 */
@RestController
@RequestMapping("/api/chat/approvals")
public class ApprovalController {

    private final ApprovalStore approvalStore;

    public ApprovalController(ApprovalStore approvalStore) {
        this.approvalStore = approvalStore;
    }

    @PostMapping("/{id}/decide")
    public ResponseEntity<ApiResponse<?>> decide(
            @PathVariable String id, @RequestBody ApprovalDecisionRequest req) {
        if (req == null || req.getDecision() == null || req.getDecision().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "decision is required (approve|deny)"));
        }
        String d = req.getDecision().trim().toLowerCase();
        boolean approve;
        if ("approve".equals(d)) {
            approve = true;
        } else if ("deny".equals(d)) {
            approve = false;
        } else {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("VALIDATION_ERROR", "decision must be approve or deny"));
        }

        return approvalStore.decide(id, approve)
                .<ResponseEntity<ApiResponse<?>>>map(a -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("id", a.id());
                    data.put("sessionId", a.sessionId());
                    data.put("tool", a.tool());
                    data.put("status", a.status().name());
                    return ResponseEntity.ok(ApiResponse.success(
                            approve ? "Approved" : "Denied", data));
                })
                .orElseGet(() -> ResponseEntity.status(404)
                        .body(ApiResponse.error("NOT_FOUND",
                                "Approval not found or already decided: " + id)));
    }
}
