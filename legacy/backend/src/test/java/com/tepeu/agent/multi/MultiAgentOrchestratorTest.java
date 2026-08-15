package com.tepeu.agent.multi;

import com.tepeu.agent.tool.CommandOutputStore;
import com.tepeu.agent.tool.DeleteFileTool;
import com.tepeu.agent.tool.ListDirTool;
import com.tepeu.agent.tool.ReadFileTool;
import com.tepeu.agent.tool.ReadOutputTool;
import com.tepeu.agent.tool.RunCommandTool;
import com.tepeu.agent.tool.RunSkillScriptTool;
import com.tepeu.agent.tool.SearchFileTool;
import com.tepeu.runtime.ScriptSandbox;
import com.tepeu.agent.tool.ToolEventEmitter;
import com.tepeu.agent.tool.WriteFileTool;
import com.tepeu.service.SessionService;
import com.tepeu.service.TaskService;
import com.tepeu.service.TokenCostEstimator;
import com.tepeu.service.chat.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.prompt.Prompt;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 多 Agent 流水线：成功路径与 Reviewer FAIL 可见失败。
 */
class MultiAgentOrchestratorTest {

    @TempDir
    Path temp;

    private ChatService chatService;
    private SessionService sessionService;
    private TaskService taskService;
    private MultiAgentOrchestrator orchestrator;
    private List<Map<String, Object>> events;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        sessionService = mock(SessionService.class);
        taskService = mock(TaskService.class);
        ListDirTool listDirTool = ListDirTool.forTests(temp);
        ReadFileTool readFileTool = ReadFileTool.forTests(temp);
        WriteFileTool writeFileTool = WriteFileTool.forTests(temp);
        SearchFileTool searchFileTool = SearchFileTool.forTests(temp);
        DeleteFileTool deleteFileTool = DeleteFileTool.forTests(temp);
        CommandOutputStore store = new CommandOutputStore();
        RunCommandTool runCommandTool = RunCommandTool.forTests(temp, store);
        ReadOutputTool readOutputTool = new ReadOutputTool(store);
        RunSkillScriptTool runSkillScriptTool = RunSkillScriptTool.forTests(temp, new ScriptSandbox(5000));
        orchestrator = new MultiAgentOrchestrator(
                chatService,
                new AgentRolePrompts("", "", ""),
                sessionService,
                taskService,
                new TokenCostEstimator(),
                listDirTool, readFileTool, writeFileTool,
                searchFileTool, deleteFileTool, runCommandTool, readOutputTool, runSkillScriptTool);
        events = new ArrayList<>();
    }

    @Test
    void run_allPass_emitsSucceeded() {
        when(chatService.chatTurn(eq("openai"), any(Prompt.class)))
                .thenReturn(new ChatService.TurnResult("1. do A\nPLAN_DONE", 10, 5));
        when(chatService.chatWithToolsTurn(eq("openai"), any(Prompt.class), any(), eq("sess-1"), isNull()))
                .thenReturn(new ChatService.TurnResult("Implemented A", 20, 10));
        when(chatService.chatWithToolsTurn(
                eq("openai"), any(Prompt.class), any(), eq("sess-1"), eq(MultiAgentOrchestrator.REVIEWER_TOOLS)))
                .thenReturn(new ChatService.TurnResult("Looks good.\nVERDICT: PASS", 15, 8));

        String status = orchestrator.run(
                "openai",
                new Goal("Add hello.txt", "file exists"),
                "ws-1",
                "sess-1",
                ToolEventEmitter.NOOP,
                events::add);

        assertEquals("succeeded", status);
        assertTrue(events.stream().anyMatch(e -> "multi_agent_start".equals(e.get("type"))));
        assertEquals(3, events.stream().filter(e -> "agent_role_start".equals(e.get("type"))).count());
        assertTrue(events.stream().anyMatch(e ->
                "multi_agent_final".equals(e.get("type")) && "succeeded".equals(e.get("status"))));
        verify(taskService, atLeastOnce()).recordTurn(eq("ws-1"), eq("sess-1"), anyString(), anyInt(), anyInt(), anyDouble());
        verify(sessionService, atLeast(3)).appendMessage(eq("sess-1"), eq("assistant"), anyString());
    }

    @Test
    void run_reviewerFail_emitsFailedVisibly() {
        when(chatService.chatTurn(eq("openai"), any(Prompt.class)))
                .thenReturn(new ChatService.TurnResult("1. step\nPLAN_DONE", 1, 1));
        when(chatService.chatWithToolsTurn(eq("openai"), any(Prompt.class), any(), eq("sess-1"), isNull()))
                .thenReturn(new ChatService.TurnResult("did nothing", 1, 1));
        when(chatService.chatWithToolsTurn(
                eq("openai"), any(Prompt.class), any(), eq("sess-1"), eq(MultiAgentOrchestrator.REVIEWER_TOOLS)))
                .thenReturn(new ChatService.TurnResult(
                        "I almost said VERDICT: PASS\nbut no.\nVERDICT: FAIL", 1, 1));

        String status = orchestrator.run(
                "openai",
                new Goal("Add hello.txt", "file exists"),
                "ws-1",
                "sess-1",
                ToolEventEmitter.NOOP,
                events::add);

        assertEquals("failed", status);
        assertTrue(events.stream().anyMatch(e ->
                "agent_role_failed".equals(e.get("type"))
                        && "REVIEWER".equals(e.get("role"))));
        assertTrue(events.stream().anyMatch(e ->
                "multi_agent_final".equals(e.get("type")) && "failed".equals(e.get("status"))));
    }

    @Test
    void run_plannerThrows_stopsPipeline() {
        when(chatService.chatTurn(eq("openai"), any(Prompt.class)))
                .thenThrow(new IllegalStateException("MISSING_API_KEY"));

        String status = orchestrator.run(
                "openai",
                new Goal("x", "y"),
                "ws-1",
                "sess-1",
                ToolEventEmitter.NOOP,
                events::add);

        assertEquals("failed", status);
        verify(chatService, never()).chatWithToolsTurn(any(), any(), any(), any(), any());
        assertTrue(events.stream().anyMatch(e ->
                "agent_role_failed".equals(e.get("type"))
                        && "PLANNER".equals(e.get("role"))));
    }
}
