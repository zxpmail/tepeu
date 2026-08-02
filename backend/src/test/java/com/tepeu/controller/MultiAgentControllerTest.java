package com.tepeu.controller;

import com.tepeu.agent.multi.MultiAgentOrchestrator;
import com.tepeu.dto.MultiAgentRunRequest;
import com.tepeu.service.BudgetService;
import com.tepeu.service.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** MultiAgentController 校验与预算门禁。 */
class MultiAgentControllerTest {

    private MultiAgentOrchestrator orchestrator;
    private SessionService sessionService;
    private BudgetService budgetService;
    private MultiAgentController controller;

    @BeforeEach
    void setUp() {
        orchestrator = mock(MultiAgentOrchestrator.class);
        sessionService = mock(SessionService.class);
        budgetService = mock(BudgetService.class);
        when(budgetService.isBlocked(anyString())).thenReturn(false);
        controller = new MultiAgentController(
                orchestrator, sessionService, budgetService, new ObjectMapper());
    }

    @Test
    void stream_missingGoal_doesNotRunOrchestrator() {
        MultiAgentRunRequest req = new MultiAgentRunRequest();
        req.setWorkspaceId("ws-1");
        req.setProvider("openai");
        SseEmitter emitter = controller.stream(req);
        assertNotNull(emitter);
        verifyNoInteractions(orchestrator);
    }

    @Test
    void stream_budgetBlocked_skipsOrchestrator() {
        when(budgetService.isBlocked("ws-1")).thenReturn(true);
        MultiAgentRunRequest req = new MultiAgentRunRequest();
        req.setGoal("do it");
        req.setWorkspaceId("ws-1");
        req.setProvider("openai");
        controller.stream(req);
        verifyNoInteractions(orchestrator);
        verify(sessionService, never()).createSession(anyString(), anyString());
    }
}
