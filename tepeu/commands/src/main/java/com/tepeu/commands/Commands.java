package com.tepeu.commands;

import com.tepeu.dispatch.Dispatch;
import com.tepeu.identity.InvokeContext;
import com.tepeu.policy.ApprovalRecord;
import com.tepeu.policy.ApprovalStore;
import com.tepeu.session.LedgerEntry;
import com.tepeu.session.Session;
import com.tepeu.session.SessionEvent;
import com.tepeu.session.SessionEventType;
import com.tepeu.syscall.SyscallResult;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 斜杠命令表。host 把 {@code /} 行交给 {@link #execute}，返回印给操作者的文本。
 * {@code /approve} 只收本会话的 approvalId；{@code /status} 只读账、不打正文；
 * {@code /btw} 旁问——看当前对话、不写事件日志、不调工具、用量进流水；
 * {@code /compact} 压缩——问模型要摘要、记 COMPACT 事件，账只追加不删，均仅 loop 空闲时可用。
 */
public final class Commands {

    private static final String STATE_KEY = "loop.state";
    private static final String LLM_GENERATE = "llm.generate";
    private static final String UP_TO_ATTR = "compact.upTo";
    private static final String COMPACT_QUESTION =
            "请把以上对话总结成一段简短摘要，保留关键事实与未决事项，供后续对话作为历史背景。";

    private static final String HELP = String.join("\n",
            "/help                       本表",
            "/approve <approvalId>      批准一条审批；重发原请求即生效",
            "/status                    账本摘要（不打正文）",
            "/btw <问题>                旁问：看当前对话；不写日志、不调工具；仅空闲时可用",
            "/compact                   压缩：把此前对话压成摘要记入账；仅空闲时可用");

    private final ApprovalStore approvals;
    private final Dispatch dispatch;

    public Commands(ApprovalStore approvals, Dispatch dispatch) {
        this.approvals = Objects.requireNonNull(approvals, "approvals");
        this.dispatch = Objects.requireNonNull(dispatch, "dispatch");
    }

    /** 处理一行斜杠命令。输入错误回用法文本，不抛。 */
    public String execute(Session session, String line) {
        Objects.requireNonNull(session, "session");
        String trimmed = line == null ? "" : line.trim();
        if (!trimmed.startsWith("/")) {
            return HELP;
        }
        String body = trimmed.substring(1);
        int space = body.indexOf(' ');
        String name = space < 0 ? body : body.substring(0, space);
        String rest = space < 0 ? "" : body.substring(space + 1).trim();
        return switch (name) {
            case "help" -> HELP;
            case "approve" -> approve(session, rest);
            case "status" -> status(session);
            case "btw" -> btw(session, rest);
            case "compact" -> compact(session);
            default -> "未知命令 " + name + "。\n" + HELP;
        };
    }

    private String approve(Session session, String id) {
        if (id.isEmpty()) {
            return "用法：/approve <approvalId>";
        }
        Optional<ApprovalRecord> record = approvals.get(id);
        if (record.isEmpty()) {
            return "没有这条审批：" + id;
        }
        if (!record.get().sessionId().equals(session.id().value())) {
            return "只许本会话的审批：" + id;
        }
        if (record.get().decided()) {
            return "已决策过：" + id;
        }
        approvals.decide(id, true, "operator");
        session.audit().record("operator", "approve", id);
        return "已批准 " + id + "。重发原请求即生效。";
    }

    private String status(Session session) {
        StringBuilder out = new StringBuilder();
        out.append("会话 ").append(session.id().value())
                .append(" ｜ 主人 ").append(session.owner().id().value())
                .append(" ｜ 工作区 ").append(session.workspace().value()).append('\n');
        out.append("循环 ").append(session.registers().get(STATE_KEY).orElse("idle")).append('\n');

        List<SessionEvent> events = session.log().readAll();
        Map<String, Long> counts = events.stream().collect(Collectors.groupingBy(
                e -> e.type().name(), TreeMap::new, Collectors.counting()));
        out.append("事件 ").append(events.size()).append(" 条");
        counts.forEach((type, n) -> out.append("  ").append(type).append('=').append(n));
        out.append('\n');

        List<LedgerEntry> ledger = session.ledger().readAll();
        long tokens = ledger.stream().mapToLong(e -> e.usage().totalTokens()).sum();
        out.append("用量 ").append(ledger.size()).append(" 笔 / ").append(tokens).append(" tokens\n");

        List<ApprovalRecord> pending = approvals.records().stream()
                .filter(r -> r.sessionId().equals(session.id().value()))
                .filter(r -> !r.decided())
                .toList();
        out.append("未决审批 ").append(pending.size());
        for (ApprovalRecord r : pending) {
            out.append("\n  ").append(r.approvalId()).append("  ").append(r.syscallName());
        }
        return out.toString();
    }

    private String btw(Session session, String question) {
        if (question.isEmpty()) {
            return "用法：/btw <问题>";
        }
        String state = session.registers().get(STATE_KEY).orElse("idle");
        if (!"idle".equals(state)) {
            return "主任务运行中，/btw 稍后再试。";
        }
        InvokeContext ctx = new InvokeContext(session.owner(), session.workspace(), session.id());
        SyscallResult result = dispatch.invoke(ctx, LLM_GENERATE, Map.of("question", question));
        result.usage().ifPresent(u -> session.ledger().record(LLM_GENERATE, u));
        if (!result.ok()) {
            return "btw 失败：" + result.errorCode().orElse("UNKNOWN");
        }
        return result.output();
    }

    /** 压缩：问模型要摘要，记 COMPACT 事件（摘要入 body，压缩点入 attrs）。账只追加，旧对话不删。 */
    private String compact(Session session) {
        String state = session.registers().get(STATE_KEY).orElse("idle");
        if (!"idle".equals(state)) {
            return "主任务运行中，/compact 稍后再试。";
        }
        List<SessionEvent> events = session.log().readAll();
        long upTo = events.isEmpty() ? 0L : events.getLast().seq();
        InvokeContext ctx = new InvokeContext(session.owner(), session.workspace(), session.id());
        SyscallResult result = dispatch.invoke(ctx, LLM_GENERATE, Map.of("question", COMPACT_QUESTION));
        result.usage().ifPresent(u -> session.ledger().record(LLM_GENERATE, u));
        if (!result.ok()) {
            return "压缩失败：" + result.errorCode().orElse("UNKNOWN");
        }
        session.log().append(SessionEventType.COMPACT, result.output(), Map.of(UP_TO_ATTR, Long.toString(upTo)));
        return "已压缩：压缩点 " + upTo + "，摘要 " + result.output().length() + " 字。";
    }
}
