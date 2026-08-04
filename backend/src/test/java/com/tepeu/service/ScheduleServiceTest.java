package com.tepeu.service;

import com.tepeu.agent.AgentOrchestrator;
import com.tepeu.agent.hook.ApprovalStore;
import com.tepeu.config.LlmProviderConfig;
import com.tepeu.model.AgentSchedule;
import com.tepeu.model.Session;
import com.tepeu.model.Workspace;
import com.tepeu.repository.AgentScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 自主任务创建/校验与执行状态更新。 */
class ScheduleServiceTest {

    private AgentScheduleRepository repository;
    private WorkspaceService workspaceService;
    private SessionService sessionService;
    private BudgetService budgetService;
    private AgentOrchestrator orchestrator;
    private ApprovalStore approvalStore;
    private TaskService taskService;
    private TaskEventNotifier taskEvents;
    private ScheduleService service;

    @BeforeEach
    void setUp() {
        repository = mock(AgentScheduleRepository.class);
        workspaceService = mock(WorkspaceService.class);
        sessionService = mock(SessionService.class);
        budgetService = mock(BudgetService.class);
        orchestrator = mock(AgentOrchestrator.class);
        approvalStore = new ApprovalStore(0L);
        taskService = mock(TaskService.class);
        taskEvents = mock(TaskEventNotifier.class);
        TokenCostEstimator costEstimator = new TokenCostEstimator();
        LlmProviderConfig providerConfig = new LlmProviderConfig();
        LlmProviderConfig.Provider deepseek = new LlmProviderConfig.Provider();
        deepseek.setId("deepseek");
        deepseek.setName("DeepSeek");
        providerConfig.setProviders(List.of(deepseek));

        service = new ScheduleService(
                repository, workspaceService, sessionService, budgetService, orchestrator,
                approvalStore, taskService, costEstimator, providerConfig, taskEvents, false, 30);
        when(workspaceService.getWorkspace("ws-1"))
                .thenReturn(Optional.of(new Workspace("ws-1", "W", null, "personal", "local")));
    }

    @Test
    void create_setsNextRunWhenEnabled() {
        when(repository.insert(any())).thenAnswer(inv -> inv.getArgument(0));

        AgentSchedule s = service.create("ws-1", "日报", "写一份摘要", "deepseek", 30, true);

        assertEquals("日报", s.getName());
        assertEquals(30, s.getIntervalMinutes());
        assertTrue(s.isEnabled());
        assertNotNull(s.getNextRunAt());
        assertFalse(s.getNextRunAt().isAfter(LocalDateTime.now().plusSeconds(2)));
        verify(repository).insert(any());
    }

    @Test
    void create_rejectsUnknownProvider() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create("ws-1", "x", "prompt", "no-such-provider", 10, true));
    }

    @Test
    void create_rejectsShortInterval() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create("ws-1", "x", "prompt", "deepseek", 0, true));
    }

    @Test
    void execute_success_updatesStatusAndSession() {
        AgentSchedule s = baseSchedule();
        when(repository.findById("sch-1")).thenReturn(Optional.of(s));
        when(repository.update(any())).thenAnswer(inv -> Optional.of(inv.getArgument(0)));
        when(budgetService.isBlocked("ws-1")).thenReturn(false);

        Session session = new Session("sess-1", "ws-1", "自主 · 日报");
        when(sessionService.createSession(eq("ws-1"), anyString())).thenReturn(session);
        when(sessionService.listMessages("sess-1")).thenReturn(List.of());
        when(orchestrator.streamTurn(anyString(), anyList(), any(), any(), anyString(), any(), anyString()))
                .thenReturn(Flux.just(textChunk("摘要完成")));

        service.execute("sch-1", true);

        assertEquals("SUCCESS", s.getLastStatus());
        assertEquals("sess-1", s.getLastSessionId());
        assertTrue(approvalStore.isAutonomous("sess-1"));
        verify(sessionService).appendMessage("sess-1", "user", "写一份摘要");
        verify(sessionService).appendMessage("sess-1", "assistant", "摘要完成");
        verify(taskEvents).publish(eventOfType("task_completed"));
    }

    @Test
    void execute_emptyReply_marksEmpty() {
        AgentSchedule s = baseSchedule();
        when(repository.findById("sch-1")).thenReturn(Optional.of(s));
        when(repository.update(any())).thenAnswer(inv -> Optional.of(inv.getArgument(0)));
        when(budgetService.isBlocked("ws-1")).thenReturn(false);
        Session session = new Session("sess-1", "ws-1", "自主 · 日报");
        when(sessionService.createSession(eq("ws-1"), anyString())).thenReturn(session);
        when(sessionService.listMessages("sess-1")).thenReturn(List.of());
        when(orchestrator.streamTurn(anyString(), anyList(), any(), any(), anyString(), any(), anyString()))
                .thenReturn(Flux.empty());

        service.execute("sch-1", true);

        assertEquals("EMPTY", s.getLastStatus());
        assertNotNull(s.getLastError());
        verify(sessionService, never()).appendMessage(eq("sess-1"), eq("assistant"), anyString());
        verify(taskEvents).publish(eventOfType("task_failed"));
    }

    @Test
    void execute_budgetBlocked_marksFailed() {
        AgentSchedule s = baseSchedule();
        when(repository.findById("sch-1")).thenReturn(Optional.of(s));
        when(repository.update(any())).thenAnswer(inv -> Optional.of(inv.getArgument(0)));
        when(budgetService.isBlocked("ws-1")).thenReturn(true);

        service.execute("sch-1", true);

        assertEquals("FAILED", s.getLastStatus());
        assertTrue(s.getLastError().contains("预算"));
        verify(sessionService, never()).createSession(anyString(), anyString());
        verify(taskEvents).publish(eventOfType("task_failed"));
    }

    @Test
    void recoverStaleRunning_marksFailed() {
        AgentSchedule s = baseSchedule();
        s.setLastStatus("RUNNING");
        s.setLastRunAt(LocalDateTime.now().minusHours(2));
        when(repository.findStaleRunning(any())).thenReturn(List.of(s));
        when(repository.update(any())).thenAnswer(inv -> Optional.of(inv.getArgument(0)));

        service.recoverStaleRunning();

        assertEquals("FAILED", s.getLastStatus());
        assertTrue(s.getLastError().contains("Recovered"));
        verify(taskEvents).publish(eventOfType("task_failed"));
    }

    @Test
    void runNow_persistsRunningBeforeReturn() {
        AgentSchedule s = baseSchedule();
        when(repository.findById("sch-1")).thenReturn(Optional.of(s));
        when(repository.update(any())).thenAnswer(inv -> Optional.of(inv.getArgument(0)));
        when(budgetService.isBlocked("ws-1")).thenReturn(false);
        Session session = new Session("sess-1", "ws-1", "自主 · 日报");
        when(sessionService.createSession(eq("ws-1"), anyString())).thenReturn(session);
        when(sessionService.listMessages("sess-1")).thenReturn(List.of());
        // 延迟完成，保证 runNow 返回时仍为 RUNNING
        when(orchestrator.streamTurn(anyString(), anyList(), any(), any(), anyString(), any(), anyString()))
                .thenReturn(Flux.<ChatResponse>empty().delaySubscription(java.time.Duration.ofSeconds(5)));

        AgentSchedule returned = service.runNow("sch-1");
        assertEquals("RUNNING", returned.getLastStatus());
    }

    private static ChatResponse textChunk(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    /** 匹配 type 字段等于给定值的任务事件 payload。 */
    private static Map<String, Object> eventOfType(String type) {
        return argThat(p -> p != null && type.equals(p.get("type")));
    }

    private static AgentSchedule baseSchedule() {
        AgentSchedule s = new AgentSchedule();
        s.setId("sch-1");
        s.setWorkspaceId("ws-1");
        s.setName("日报");
        s.setPrompt("写一份摘要");
        s.setProviderId("deepseek");
        s.setIntervalMinutes(60);
        s.setEnabled(true);
        s.setLastStatus("IDLE");
        s.setNextRunAt(LocalDateTime.now().minusMinutes(1));
        return s;
    }
}
