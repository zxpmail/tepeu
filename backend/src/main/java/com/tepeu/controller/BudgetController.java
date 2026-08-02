package com.tepeu.controller;

import com.tepeu.dto.ApiResponse;
import com.tepeu.dto.BudgetUpdateRequest;
import com.tepeu.service.BudgetService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 成本/预算 API（Spec M2.4）。
 * 关联：BudgetService、CostDashboardView。
 */
@RestController
@RequestMapping("/api")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    /** 工作区成本仪表盘（用量 + 预算状态）。 */
    @GetMapping("/workspace/{id}/cost")
    public ResponseEntity<ApiResponse<?>> costDashboard(@PathVariable String id) {
        try {
            return ResponseEntity.ok(ApiResponse.success(budgetService.status(id)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(ApiResponse.error("NOT_FOUND", e.getMessage()));
        }
    }

    @PutMapping("/workspace/{id}/budget")
    public ResponseEntity<ApiResponse<?>> updateBudget(
            @PathVariable String id, @RequestBody BudgetUpdateRequest req) {
        try {
            BudgetService.BudgetStatus status = budgetService.save(
                    id,
                    req != null ? req.getBudgetUsd() : null,
                    req != null ? req.getHardLimit() : null,
                    req != null ? req.getAlertThreshold() : null);
            return ResponseEntity.ok(ApiResponse.success("Budget updated", status));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() == null ? "invalid" : e.getMessage();
            if (msg.startsWith("工作区不存在") || msg.startsWith("Workspace not found")) {
                return ResponseEntity.status(404).body(ApiResponse.error("NOT_FOUND", msg));
            }
            return ResponseEntity.badRequest().body(ApiResponse.error("VALIDATION_ERROR", msg));
        }
    }
}
