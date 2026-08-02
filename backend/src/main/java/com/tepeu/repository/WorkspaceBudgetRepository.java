package com.tepeu.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 工作区预算配置持久化。
 * 关联：BudgetService、DatabaseConfig.workspace_budget。
 */
@Repository
public class WorkspaceBudgetRepository {

    public record BudgetRow(
            String workspaceId,
            Double budgetUsd,
            boolean hardLimit,
            double alertThreshold,
            LocalDateTime updatedAt) {}

    private final JdbcTemplate jdbc;
    private final RowMapper<BudgetRow> mapper = (rs, rowNum) -> {
        double budget = rs.getDouble("budget_usd");
        Double budgetUsd = rs.wasNull() ? null : budget;
        return new BudgetRow(
                rs.getString("workspace_id"),
                budgetUsd,
                rs.getInt("hard_limit") != 0,
                rs.getDouble("alert_threshold"),
                rs.getObject("updated_at", LocalDateTime.class));
    };

    public WorkspaceBudgetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<BudgetRow> findByWorkspaceId(String workspaceId) {
        List<BudgetRow> rows = jdbc.query(
                "SELECT * FROM workspace_budget WHERE workspace_id = ?", mapper, workspaceId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public BudgetRow upsert(String workspaceId, Double budgetUsd, boolean hardLimit, double alertThreshold) {
        LocalDateTime now = LocalDateTime.now();
        int updated = jdbc.update(
                """
                UPDATE workspace_budget
                SET budget_usd = ?, hard_limit = ?, alert_threshold = ?, updated_at = ?
                WHERE workspace_id = ?
                """,
                budgetUsd, hardLimit ? 1 : 0, alertThreshold, now, workspaceId);
        if (updated == 0) {
            jdbc.update(
                    """
                    INSERT INTO workspace_budget (workspace_id, budget_usd, hard_limit, alert_threshold, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    """,
                    workspaceId, budgetUsd, hardLimit ? 1 : 0, alertThreshold, now);
        }
        return findByWorkspaceId(workspaceId).orElseThrow();
    }
}
