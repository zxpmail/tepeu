package com.tepeu.service;

import com.tepeu.model.Workspace;
import com.tepeu.repository.WorkspaceBudgetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 预算告警与硬门禁逻辑。 */
class BudgetServiceTest {

    private WorkspaceBudgetRepository budgetRepository;
    private TaskService taskService;
    private WorkspaceService workspaceService;
    private BudgetService service;

    @BeforeEach
    void setUp() {
        budgetRepository = mock(WorkspaceBudgetRepository.class);
        taskService = mock(TaskService.class);
        workspaceService = mock(WorkspaceService.class);
        service = new BudgetService(budgetRepository, taskService, workspaceService);
        when(workspaceService.getWorkspace("ws-1"))
                .thenReturn(Optional.of(new Workspace("ws-1", "W", null, "personal", "local")));
        when(taskService.getWorkspaceStats("ws-1"))
                .thenReturn(new TaskService.WorkspaceStats(1000L, 8.0, 5));
    }

    @Test
    void noBudget_notAlertNotBlocked() {
        when(budgetRepository.findByWorkspaceId("ws-1")).thenReturn(Optional.empty());
        BudgetService.BudgetStatus s = service.status("ws-1");
        assertFalse(s.alert());
        assertFalse(s.blocked());
        assertNull(s.budgetUsd());
    }

    @Test
    void overThreshold_alerts() {
        when(budgetRepository.findByWorkspaceId("ws-1")).thenReturn(Optional.of(
                new WorkspaceBudgetRepository.BudgetRow("ws-1", 10.0, false, 0.8, null)));
        BudgetService.BudgetStatus s = service.status("ws-1");
        assertTrue(s.alert());
        assertFalse(s.blocked());
        assertEquals(0.8, s.usageRatio(), 1e-9);
    }

    @Test
    void hardLimitExceeded_blocks() {
        when(budgetRepository.findByWorkspaceId("ws-1")).thenReturn(Optional.of(
                new WorkspaceBudgetRepository.BudgetRow("ws-1", 5.0, true, 0.8, null)));
        assertTrue(service.isBlocked("ws-1"));
        BudgetService.BudgetStatus s = service.status("ws-1");
        assertTrue(s.blocked());
        assertTrue(s.alert());
    }

    @Test
    void save_persistsAndReturnsStatus() {
        when(budgetRepository.upsert(eq("ws-1"), eq(20.0), eq(true), eq(0.9)))
                .thenReturn(new WorkspaceBudgetRepository.BudgetRow("ws-1", 20.0, true, 0.9, null));
        when(budgetRepository.findByWorkspaceId("ws-1")).thenReturn(Optional.of(
                new WorkspaceBudgetRepository.BudgetRow("ws-1", 20.0, true, 0.9, null)));

        BudgetService.BudgetStatus s = service.save("ws-1", 20.0, true, 0.9);
        assertEquals(20.0, s.budgetUsd());
        assertTrue(s.hardLimit());
        verify(budgetRepository).upsert("ws-1", 20.0, true, 0.9);
    }

    @Test
    void zeroBudget_soft_alertsButNotBlocked() {
        when(budgetRepository.findByWorkspaceId("ws-1")).thenReturn(Optional.of(
                new WorkspaceBudgetRepository.BudgetRow("ws-1", 0.0, false, 0.8, null)));
        BudgetService.BudgetStatus s = service.status("ws-1");
        assertTrue(s.alert());
        assertFalse(s.blocked());
        assertFalse(service.isBlocked("ws-1"));
    }

    @Test
    void zeroBudget_hard_blocksImmediately() {
        when(budgetRepository.findByWorkspaceId("ws-1")).thenReturn(Optional.of(
                new WorkspaceBudgetRepository.BudgetRow("ws-1", 0.0, true, 0.8, null)));
        assertTrue(service.isBlocked("ws-1"));
        BudgetService.BudgetStatus s = service.status("ws-1");
        assertTrue(s.blocked());
        assertTrue(s.alert());
    }
}
