package com.tepeu.service;

import com.tepeu.repository.WorkspaceBudgetRepository;
import org.springframework.stereotype.Service;

/**
 * 工作区预算：告警阈值 + 可选硬门禁（Spec M2.4）。
 * 无预算行 / budgetUsd=null = 不限；budgetUsd=0 = 零预算（始终告警，硬门禁下直接阻断）；
 * 硬门禁开启且费用 ≥ 预算时阻断新对话。
 * 关联：TaskService、ChatController、CostDashboard。
 */
@Service
public class BudgetService {

    public record BudgetStatus(
            String workspaceId,
            long totalTokens,
            double totalCostUsd,
            int turnCount,
            Double budgetUsd,
            boolean hardLimit,
            double alertThreshold,
            double usageRatio,
            boolean alert,
            boolean blocked) {}

    private final WorkspaceBudgetRepository budgetRepository;
    private final TaskService taskService;
    private final WorkspaceService workspaceService;

    public BudgetService(
            WorkspaceBudgetRepository budgetRepository,
            TaskService taskService,
            WorkspaceService workspaceService) {
        this.budgetRepository = budgetRepository;
        this.taskService = taskService;
        this.workspaceService = workspaceService;
    }

    /** 仪表盘状态：用量 + 预算判定。 */
    public BudgetStatus status(String workspaceId) {
        requireWorkspace(workspaceId);
        TaskService.WorkspaceStats usage = taskService.getWorkspaceStats(workspaceId);
        var cfg = budgetRepository.findByWorkspaceId(workspaceId);
        Double budget = cfg.map(WorkspaceBudgetRepository.BudgetRow::budgetUsd).orElse(null);
        boolean hard = cfg.map(WorkspaceBudgetRepository.BudgetRow::hardLimit).orElse(false);
        double threshold = cfg.map(WorkspaceBudgetRepository.BudgetRow::alertThreshold).orElse(0.8);
        if (threshold <= 0 || threshold > 1) {
            threshold = 0.8;
        }

        double cost = usage.totalCostUsd();
        double ratio = 0;
        boolean alert = false;
        boolean blocked = false;
        // budgetUsd == null：不限额；== 0：零预算（硬门禁下直接阻断，并始终告警）
        if (budget != null && budget >= 0) {
            if (budget == 0) {
                ratio = cost > 0 ? 1.0 : 0.0;
                alert = true;
                blocked = hard;
            } else {
                ratio = cost / budget;
                alert = ratio >= threshold;
                blocked = hard && cost >= budget;
            }
        }
        return new BudgetStatus(
                workspaceId,
                usage.totalTokens(),
                cost,
                usage.turnCount(),
                budget,
                hard,
                threshold,
                ratio,
                alert,
                blocked);
    }

    /** 保存预算；budgetUsd 为 null 表示清除限额（仍可保留行或写 null）。 */
    public BudgetStatus save(
            String workspaceId, Double budgetUsd, Boolean hardLimit, Double alertThreshold) {
        requireWorkspace(workspaceId);
        var existing = budgetRepository.findByWorkspaceId(workspaceId);
        boolean hard = hardLimit != null
                ? hardLimit
                : existing.map(WorkspaceBudgetRepository.BudgetRow::hardLimit).orElse(false);
        double threshold = alertThreshold != null
                ? alertThreshold
                : existing.map(WorkspaceBudgetRepository.BudgetRow::alertThreshold).orElse(0.8);
        if (threshold <= 0 || threshold > 1) {
            throw new IllegalArgumentException("alertThreshold must be in (0, 1]");
        }
        if (budgetUsd != null && budgetUsd < 0) {
            throw new IllegalArgumentException("budgetUsd must be >= 0");
        }
        budgetRepository.upsert(workspaceId, budgetUsd, hard, threshold);
        return status(workspaceId);
    }

    /** 硬门禁：已超预算则不可开新回合。 */
    public boolean isBlocked(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return false;
        }
        return status(workspaceId).blocked();
    }

    private void requireWorkspace(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        if (workspaceService.getWorkspace(workspaceId).isEmpty()) {
            throw new IllegalArgumentException("工作区不存在：" + workspaceId);
        }
    }
}
