package com.tepeu.load;

import com.tepeu.commands.Commands;
import com.tepeu.dispatch.Dispatch;
import com.tepeu.execution.Workspace;
import com.tepeu.identity.Principal;
import com.tepeu.identity.PrincipalId;
import com.tepeu.identity.WorkspaceId;
import com.tepeu.llm.gateway.LlmBackend;
import com.tepeu.llm.gateway.LlmGateway;
import com.tepeu.loop.Loop;
import com.tepeu.persist.Persist;
import com.tepeu.policy.ApprovalStore;
import com.tepeu.policy.local.DefaultRuleMatrix;
import com.tepeu.policy.persist.PersistedApprovalStore;
import com.tepeu.session.Session;
import com.tepeu.session.SessionStore;
import com.tepeu.session.persist.PersistedSessionStore;

import java.nio.file.Path;
import java.util.Objects;

/**
 * 装配。注入实现（host 选定后交进来），拼系统提示，登记 {@code llm.generate}
 * 与工作区四操作，接上斜杠命令表。装配顺序收在这里，host 只见 {@link Wired}。
 * 会话固定默认一个；主人与工作区标识固定单机取值。
 */
public final class Assembly {

    /** 默认系统提示：身份、工作区边界与最小工具请求协议。 */
    public static final String SYSTEM_PROMPT = String.join("\n",
            "你是 Tepeu，运行在操作者本机的单用户命令行 Agent。",
            "你的活动范围只有操作者给定的工作区目录。",
            "要使用工具时，输出第一行写：@tool 名称 k=v;k=v，其余行会被忽略。",
            "可用工具：execution.fs.read、execution.fs.write、execution.proc.spawn、execution.sandbox.probe。",
            "写文件与创建进程要先经操作者批准；被拒时如实告知并停下。",
            "不使用工具时，直接输出给操作者的最终答复。");

    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("ws");

    /** 装配结果。host 持有它运行：收件箱喂输入、loop 跑轮、commands 接斜杠。 */
    public record Wired(Session session, Dispatch dispatch, Commands commands, Loop loop,
                        Path workspaceRoot) {
    }

    private Assembly() {
    }

    public static Wired wire(Persist persist, LlmBackend backend, String systemPrompt, Path workspaceRoot) {
        Objects.requireNonNull(persist, "persist");
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        Session session = new PersistedSessionStore(persist).open(
                SessionStore.DEFAULT, new Principal(new PrincipalId("u")), WORKSPACE_ID);
        ApprovalStore approvals = new PersistedApprovalStore(persist);
        Dispatch dispatch = new Dispatch(new DefaultRuleMatrix(), approvals);
        LlmGateway gateway = new LlmGateway(backend, systemPrompt);
        dispatch.register(Loop.LLM_GENERATE, (ctx, syscall) -> gateway.generate(session.log()));
        Workspace workspace = new Workspace(workspaceRoot);
        dispatch.register(Workspace.FS_READ, workspace::read);
        dispatch.register(Workspace.FS_WRITE, workspace::write);
        dispatch.register(Workspace.PROC_SPAWN, workspace::spawn);
        dispatch.register(Workspace.SANDBOX_PROBE, workspace::probe);
        Commands commands = new Commands(approvals, dispatch);
        Loop loop = new Loop(dispatch);
        return new Wired(session, dispatch, commands, loop, workspaceRoot);
    }
}
