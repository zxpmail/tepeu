package com.tepeu.controller;

import com.tepeu.dto.ApiResponse;
import com.tepeu.dto.BudgetUpdateRequest;
import com.tepeu.service.BudgetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** BudgetController 状态码与校验映射。 */
class BudgetControllerTest {

    private BudgetService budgetService;
    private BudgetController controller;

    @BeforeEach
    void setUp() {
        budgetService = mock(BudgetService.class);
        controller = new BudgetController(budgetService);
    }

    @Test
    void costDashboard_ok() {
        when(budgetService.status("ws-1")).thenReturn(
                new BudgetService.BudgetStatus("ws-1", 10, 1.0, 2, 5.0, false, 0.8, 0.2, false, false));
        ResponseEntity<?> res = controller.costDashboard("ws-1");
        assertEquals(200, res.getStatusCode().value());
        assertTrue(res.getBody() instanceof ApiResponse);
    }

    @Test
    void costDashboard_missingWorkspace_404() {
        when(budgetService.status("missing"))
                .thenThrow(new IllegalArgumentException("工作区不存在：missing"));
        assertEquals(404, controller.costDashboard("missing").getStatusCode().value());
    }

    @Test
    void updateBudget_ok() {
        when(budgetService.save(eq("ws-1"), eq(10.0), eq(true), eq(0.9))).thenReturn(
                new BudgetService.BudgetStatus("ws-1", 0, 0, 0, 10.0, true, 0.9, 0, false, false));
        BudgetUpdateRequest req = new BudgetUpdateRequest();
        req.setBudgetUsd(10.0);
        req.setHardLimit(true);
        req.setAlertThreshold(0.9);
        assertEquals(200, controller.updateBudget("ws-1", req).getStatusCode().value());
    }

    @Test
    void updateBudget_validation_400() {
        when(budgetService.save(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("budgetUsd must be >= 0"));
        BudgetUpdateRequest req = new BudgetUpdateRequest();
        req.setBudgetUsd(-1.0);
        assertEquals(400, controller.updateBudget("ws-1", req).getStatusCode().value());
    }
}
