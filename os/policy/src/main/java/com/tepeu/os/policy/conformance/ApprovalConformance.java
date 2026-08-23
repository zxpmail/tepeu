package com.tepeu.os.policy.conformance;

import com.tepeu.os.conformance.ConformanceCase;
import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.SessionId;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.policy.ApprovalStore;
import com.tepeu.os.syscall.Syscall;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.tepeu.os.conformance.ConformanceCheck.check;
import static com.tepeu.os.conformance.ConformanceCheck.checkEquals;
import static com.tepeu.os.conformance.ConformanceCheck.expectThrows;

/** ApprovalStore 契约 — 内存与 SQLite 必须全绿。 */
public final class ApprovalConformance {

    public interface StoreFactory {
        ApprovalStore newStore();
    }

    private ApprovalConformance() {
    }

    public static List<ConformanceCase> suite(StoreFactory factory) {
        List<ConformanceCase> cases = new ArrayList<>();
        cases.add(new ConformanceCase("approval", "未决 ask 幂等同一 id",
                () -> {
                    ApprovalStore s = factory.newStore();
                    TurnContext ctx = turn("s1");
                    Syscall call = new Syscall("dangerous", Map.of());
                    String a = s.ask(ctx, call);
                    String b = s.ask(ctx, call);
                    checkEquals(a, b, "幂等");
                    checkEquals(1, s.records().size(), "一条 asked");
                    check(!s.records().get(0).decided(), "未决");
                }));
        cases.add(new ConformanceCase("approval", "decide 后 consume 一次，再 consume 空",
                () -> {
                    ApprovalStore s = factory.newStore();
                    TurnContext ctx = turn("s1");
                    Syscall call = new Syscall("dangerous", Map.of());
                    String id = s.ask(ctx, call);
                    s.decide(id, true, "host");
                    checkEquals(true, s.consumeDecision(ctx, call).orElseThrow(), "放行");
                    check(s.consumeDecision(ctx, call).isEmpty(), "严格单次");
                }));
        cases.add(new ConformanceCase("approval", "decide(false) consume 得 false",
                () -> {
                    ApprovalStore s = factory.newStore();
                    TurnContext ctx = turn("s1");
                    Syscall call = new Syscall("dangerous", Map.of());
                    s.decide(s.ask(ctx, call), false, "host");
                    checkEquals(false, s.consumeDecision(ctx, call).orElseThrow(), "否决");
                }));
        cases.add(new ConformanceCase("approval", "重复 decide 拒绝",
                () -> {
                    ApprovalStore s = factory.newStore();
                    TurnContext ctx = turn("s1");
                    String id = s.ask(ctx, new Syscall("dangerous", Map.of()));
                    s.decide(id, true, "host");
                    expectThrows(IllegalStateException.class, () -> s.decide(id, true, "host"));
                }));
        cases.add(new ConformanceCase("approval", "会话隔离",
                () -> {
                    ApprovalStore s = factory.newStore();
                    TurnContext a = turn("a");
                    TurnContext b = turn("b");
                    Syscall call = new Syscall("dangerous", Map.of());
                    s.decide(s.ask(a, call), true, "host");
                    check(s.consumeDecision(a, call).isPresent(), "a 有");
                    check(s.consumeDecision(b, call).isEmpty(), "b 无");
                }));
        cases.add(new ConformanceCase("approval", "argsDigest 不同则不幂等、不能串用许可",
                () -> {
                    ApprovalStore s = factory.newStore();
                    TurnContext ctx = turn("s1");
                    Syscall a = new Syscall("dangerous", Map.of("path", "a.txt"));
                    Syscall b = new Syscall("dangerous", Map.of("path", "b.txt"));
                    String idA = s.ask(ctx, a);
                    String idB = s.ask(ctx, b);
                    check(!idA.equals(idB), "不同 args 不同 id");
                    checkEquals(idA, s.ask(ctx, a), "同 args 仍幂等");
                    s.decide(idA, true, "host");
                    check(s.consumeDecision(ctx, b).isEmpty(), "不得用 a 的许可消费 b");
                    checkEquals(true, s.consumeDecision(ctx, a).orElseThrow(), "匹配 args 可消费");
                }));
        return List.copyOf(cases);
    }

    private static TurnContext turn(String session) {
        Principal owner = Principal.personal(new PrincipalId("appr-user"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("appr-ws"));
        return new TurnContext(owner, ns, new SessionId(session), Optional.empty());
    }
}
