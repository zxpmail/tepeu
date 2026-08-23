package com.tepeu.os.bus.conformance;

import com.tepeu.os.conformance.ConformanceCase;
import com.tepeu.os.policy.ApprovalRecord;
import com.tepeu.os.policy.ApprovalRequiredException;
import com.tepeu.os.policy.ApprovalStore;
import com.tepeu.os.bus.CapabilityBus;
import com.tepeu.os.bus.GuardHook;
import com.tepeu.os.policy.PolicyDeniedException;
import com.tepeu.os.policy.PolicyVerdict;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallResult;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.identity.SessionId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.tepeu.os.conformance.ConformanceCheck.check;
import static com.tepeu.os.conformance.ConformanceCheck.checkEquals;
import static com.tepeu.os.conformance.ConformanceCheck.expectThrows;

/**
 * 能力总线 conformance 契约 — 任何实现必须全绿。
 *
 * 契约来源：底板 §2（门对称 + 卫兵组合代数 fail-closed）/ §6 红线 /
 * ADR-016 第三轮（封闭 union、异常规范化）+ 代码审计二（失败必达终态）+
 * 第九轮（C1 同步重试式审批 / C2 未装配 fail-closed / C3 失败双通道）。
 */
public final class BusConformance {

    public interface BusFactory {
        CapabilityBus newBus();

        ApprovalStore newApprovalStore();
    }

    private BusConformance() {
    }

    public static List<ConformanceCase> suite(BusFactory factory) {
        List<ConformanceCase> cases = new ArrayList<>();

        // ---- dispatch ----
        cases.add(new ConformanceCase("dispatch", "注册→分发往返，结果即处理器输出",
                () -> {
                    CapabilityBus bus = wiredAllowBus(factory);
                    bus.register("echo", (c, call) ->
                            SyscallResult.success(call.args().getOrDefault("text", "")));
                    SyscallResult r = bus.invoke(turn(), new Syscall("echo", Map.of("text", "hi")));
                    check(r.ok(), "应成功: " + r.errorCode().orElse(""));
                    checkEquals("hi", r.output(), "往返输出");
                }));
        cases.add(new ConformanceCase("dispatch", "重复注册同名 syscall 拒绝",
                () -> {
                    CapabilityBus bus = factory.newBus();
                    bus.register("echo", (c, call) -> SyscallResult.success("ok"));
                    expectThrows(IllegalStateException.class,
                            () -> bus.register("echo", (c, call) -> SyscallResult.success("dup")));
                }));
        cases.add(new ConformanceCase("dispatch", "未注册 syscall 失败可见（NOT_FOUND 结果，不抛穿）",
                () -> {
                    CapabilityBus bus = wiredAllowBus(factory);
                    SyscallResult r = bus.invoke(turn(), new Syscall("nope", Map.of()));
                    check(!r.ok(), "未注册必须失败可见");
                    checkEquals("NOT_FOUND", r.errorCode().orElse(""), "错误码");
                }));
        cases.add(new ConformanceCase("dispatch", "registeredSyscalls 为排序后的规范序（禁哈希迭代序）",
                () -> {
                    CapabilityBus bus = factory.newBus();
                    bus.register("zeta", (c, call) -> SyscallResult.success("z"));
                    bus.register("alpha", (c, call) -> SyscallResult.success("a"));
                    bus.register("mu", (c, call) -> SyscallResult.success("m"));
                    checkEquals(List.of("alpha", "mu", "zeta"), bus.registeredSyscalls(), "规范序");
                }));

        // ---- C2 fail-closed（第九轮：未装配即拒绝，无默认放行）----
        cases.add(new ConformanceCase("fail-closed", "未装配 Policy：一切调用拒绝（C2，无默认 ALLOW）",
                () -> {
                    CapabilityBus bus = factory.newBus();
                    bus.register("echo", (c, call) -> SyscallResult.success("ok"));
                    PolicyDeniedException ex = expectThrows(PolicyDeniedException.class,
                            () -> bus.invoke(turn(), new Syscall("echo", Map.of())));
                    checkEquals(PolicyVerdict.DENY, ex.verdict(), "未装配应表现为 DENY");
                }));
        cases.add(new ConformanceCase("fail-closed", "Policy DENY 抛 PolicyDeniedException",
                () -> {
                    CapabilityBus bus = factory.newBus();
                    bus.register("echo", (c, call) -> SyscallResult.success("ok"));
                    bus.setPolicyHook((c, call) -> PolicyVerdict.DENY);
                    PolicyDeniedException ex = expectThrows(PolicyDeniedException.class,
                            () -> bus.invoke(turn(), new Syscall("echo", Map.of())));
                    checkEquals(PolicyVerdict.DENY, ex.verdict(), "verdict");
                }));
        cases.add(new ConformanceCase("fail-closed", "NEED_APPROVAL 在无审批通道时同样拒绝（fail-closed）",
                () -> {
                    CapabilityBus bus = factory.newBus();
                    bus.register("echo", (c, call) -> SyscallResult.success("ok"));
                    bus.setPolicyHook((c, call) -> PolicyVerdict.NEED_APPROVAL);
                    PolicyDeniedException ex = expectThrows(PolicyDeniedException.class,
                            () -> bus.invoke(turn(), new Syscall("echo", Map.of())));
                    checkEquals(PolicyVerdict.NEED_APPROVAL, ex.verdict(),
                            "无通道的需审批应保持 NEED_APPROVAL 语义");
                }));
        cases.add(new ConformanceCase("fail-closed", "Policy 钩子异常规范化为 DENY，不穿透",
                () -> {
                    CapabilityBus bus = factory.newBus();
                    bus.register("echo", (c, call) -> SyscallResult.success("ok"));
                    bus.setPolicyHook((c, call) -> {
                        throw new IllegalStateException("policy broken");
                    });
                    PolicyDeniedException ex = expectThrows(PolicyDeniedException.class,
                            () -> bus.invoke(turn(), new Syscall("echo", Map.of())));
                    checkEquals(PolicyVerdict.DENY, ex.verdict(), "异常→DENY");
                }));
        cases.add(new ConformanceCase("fail-closed", "Policy 返回 null 规范化为 DENY（词汇表外）",
                () -> {
                    CapabilityBus bus = factory.newBus();
                    bus.register("echo", (c, call) -> SyscallResult.success("ok"));
                    bus.setPolicyHook((c, call) -> null);
                    expectThrows(PolicyDeniedException.class,
                            () -> bus.invoke(turn(), new Syscall("echo", Map.of())));
                }));
        cases.add(new ConformanceCase("fail-closed", "卫兵异常 fail-closed（BusGuardException）",
                () -> {
                    CapabilityBus bus = wiredAllowBus(factory);
                    bus.register("echo", (c, call) -> SyscallResult.success("ok"));
                    bus.addGuardHook(new GuardHook() {
                        @Override
                        public PolicyVerdict before(TurnContext c, Syscall call) {
                            throw new IllegalStateException("guard infra broken");
                        }
                    });
                    expectThrows(com.tepeu.os.bus.BusGuardException.class,
                            () -> bus.invoke(turn(), new Syscall("echo", Map.of())));
                }));
        cases.add(new ConformanceCase("fail-closed", "已取消的 TurnContext 入口即拒",
                () -> {
                    CapabilityBus bus = wiredAllowBus(factory);
                    bus.register("echo", (c, call) -> SyscallResult.success("ok"));
                    TurnContext cancelled = turn();
                    cancelled.cancel();
                    expectThrows(com.tepeu.os.bus.BusGuardException.class,
                            () -> bus.invoke(cancelled, new Syscall("echo", Map.of())));
                }));

        // ---- C1 同步重试式审批（第九轮：ask→抛→decide→重试→放行/拒绝；许可严格单次）----
        cases.add(new ConformanceCase("approval", "NEED_APPROVAL 首调登记 asked 并抛出；未决期间重复调用幂等同 id",
                () -> {
                    CapabilityBus bus = factory.newBus();
                    ApprovalStore approvals = factory.newApprovalStore();
                    bus.setPolicyHook((c, call) -> PolicyVerdict.NEED_APPROVAL);
                    bus.setApprovalStore(approvals);
                    bus.register("dangerous", (c, call) -> SyscallResult.success("done"));
                    TurnContext ctx = turn();
                    Syscall call = new Syscall("dangerous", Map.of());
                    ApprovalRequiredException first = expectThrows(ApprovalRequiredException.class,
                            () -> bus.invoke(ctx, call));
                    ApprovalRequiredException second = expectThrows(ApprovalRequiredException.class,
                            () -> bus.invoke(ctx, call));
                    checkEquals(first.approvalId(), second.approvalId(),
                            "同 (session, syscall) 未决时 ask 幂等返回同一 id");
                    List<ApprovalRecord> records = approvals.records();
                    check(records.size() == 1, "asked 只登记一条，实得 " + records.size());
                    check(!records.get(0).decided(), "尚未决策");
                    checkEquals("dangerous", records.get(0).syscallName(), "记录绑定 syscall");
                }));
        cases.add(new ConformanceCase("approval", "decide(true) 后重试同一调用放行；许可严格单次（第三次重新 ask）",
                () -> {
                    CapabilityBus bus = factory.newBus();
                    ApprovalStore approvals = factory.newApprovalStore();
                    bus.setPolicyHook((c, call) -> PolicyVerdict.NEED_APPROVAL);
                    bus.setApprovalStore(approvals);
                    bus.register("dangerous", (c, call) -> SyscallResult.success("done"));
                    TurnContext ctx = turn();
                    Syscall call = new Syscall("dangerous", Map.of());
                    ApprovalRequiredException asked = expectThrows(ApprovalRequiredException.class,
                            () -> bus.invoke(ctx, call));
                    approvals.decide(asked.approvalId(), true, "host");
                    SyscallResult r = bus.invoke(ctx, call);
                    check(r.ok() && "done".equals(r.output()), "决策放行后重试应执行: " + r.errorCode().orElse(""));
                    // 许可已被消费：再次调用重新走审批（新 asked，而非沿用旧许可）
                    ApprovalRequiredException reAsked = expectThrows(ApprovalRequiredException.class,
                            () -> bus.invoke(ctx, call));
                    check(!reAsked.approvalId().equals(asked.approvalId()),
                            "严格单次：旧许可不得复用，须登记新 asked");
                    check(approvals.records().size() == 2, "应产生第二条 asked 记录");
                }));
        cases.add(new ConformanceCase("approval", "decide(false) 后重试同调用被拒（NEED_APPROVAL 语义）",
                () -> {
                    CapabilityBus bus = factory.newBus();
                    ApprovalStore approvals = factory.newApprovalStore();
                    bus.setPolicyHook((c, call) -> PolicyVerdict.NEED_APPROVAL);
                    bus.setApprovalStore(approvals);
                    bus.register("dangerous", (c, call) -> SyscallResult.success("done"));
                    TurnContext ctx = turn();
                    Syscall call = new Syscall("dangerous", Map.of());
                    ApprovalRequiredException asked = expectThrows(ApprovalRequiredException.class,
                            () -> bus.invoke(ctx, call));
                    approvals.decide(asked.approvalId(), false, "host");
                    PolicyDeniedException ex = expectThrows(PolicyDeniedException.class,
                            () -> bus.invoke(ctx, call));
                    checkEquals(PolicyVerdict.NEED_APPROVAL, ex.verdict(), "否决表现为 NEED_APPROVAL 拒绝");
                }));
        cases.add(new ConformanceCase("approval", "审批许可按会话隔离：另一会话同 syscall 不受既决许可影响",
                () -> {
                    CapabilityBus bus = factory.newBus();
                    ApprovalStore approvals = factory.newApprovalStore();
                    bus.setPolicyHook((c, call) -> PolicyVerdict.NEED_APPROVAL);
                    bus.setApprovalStore(approvals);
                    bus.register("dangerous", (c, call) -> SyscallResult.success("done"));
                    TurnContext a = turn();
                    TurnContext b = new TurnContext(a.principal(), a.namespace(),
                            new SessionId("other-session"), Optional.empty());
                    ApprovalRequiredException asked = expectThrows(ApprovalRequiredException.class,
                            () -> bus.invoke(a, new Syscall("dangerous", Map.of())));
                    approvals.decide(asked.approvalId(), true, "host");
                    check(bus.invoke(a, new Syscall("dangerous", Map.of())).ok(), "原会话放行");
                    expectThrows(ApprovalRequiredException.class,
                            () -> bus.invoke(b, new Syscall("dangerous", Map.of())));
                }));

        // ---- C3 失败双通道：执行类失败不抛穿，失败必达终态 ----
        cases.add(new ConformanceCase("visibility", "handler 异常转为失败结果，不抛穿总线",
                () -> {
                    CapabilityBus bus = wiredAllowBus(factory);
                    bus.register("boom", (c, call) -> {
                        throw new IllegalArgumentException("bad");
                    });
                    SyscallResult r = bus.invoke(turn(), new Syscall("boom", Map.of()));
                    check(!r.ok(), "handler 异常必须失败可见");
                    checkEquals("HANDLER_ERROR", r.errorCode().orElse(""), "错误码");
                }));
        cases.add(new ConformanceCase("visibility", "handler 返回 null 同样失败可见",
                () -> {
                    CapabilityBus bus = wiredAllowBus(factory);
                    bus.register("null", (c, call) -> null);
                    SyscallResult r = bus.invoke(turn(), new Syscall("null", Map.of()));
                    check(!r.ok(), "null 结果必须失败可见");
                }));
        cases.add(new ConformanceCase("visibility", "卫兵 before→handler→after 顺序被观察",
                () -> {
                    CapabilityBus bus = wiredAllowBus(factory);
                    List<String> trace = new ArrayList<>();
                    bus.addGuardHook(new GuardHook() {
                        @Override
                        public PolicyVerdict before(TurnContext c, Syscall call) {
                            trace.add("before");
                            return PolicyVerdict.ALLOW;
                        }

                        @Override
                        public void after(TurnContext c, Syscall call, SyscallResult result) {
                            trace.add("after");
                        }
                    });
                    bus.register("echo", (c, call) -> {
                        trace.add("handler");
                        return SyscallResult.success("ok");
                    });
                    bus.invoke(turn(), new Syscall("echo", Map.of()));
                    checkEquals(List.of("before", "handler", "after"), trace, "执行顺序");
                }));
        cases.add(new ConformanceCase("visibility", "入口先于 Policy：卫兵中断时 Policy 不被调用",
                () -> {
                    CapabilityBus bus = factory.newBus();
                    bus.register("echo", (c, call) -> SyscallResult.success("ok"));
                    AtomicInteger policyCalls = new AtomicInteger();
                    bus.setPolicyHook((c, call) -> {
                        policyCalls.incrementAndGet();
                        return PolicyVerdict.ALLOW;
                    });
                    bus.addGuardHook(new GuardHook() {
                        @Override
                        public PolicyVerdict before(TurnContext c, Syscall call) {
                            throw new com.tepeu.os.bus.BusGuardException("blocked");
                        }
                    });
                    expectThrows(com.tepeu.os.bus.BusGuardException.class,
                            () -> bus.invoke(turn(), new Syscall("echo", Map.of())));
                    checkEquals(0, policyCalls.get(), "卫兵中断时 Policy 不应被调用");
                }));
        cases.add(new ConformanceCase("guard-verdict", "deny > ask > allow：任一 DENY 即拒且不 ask",
                () -> {
                    CapabilityBus bus = factory.newBus();
                    ApprovalStore approvals = factory.newApprovalStore();
                    bus.setPolicyHook((c, call) -> PolicyVerdict.ALLOW);
                    bus.setApprovalStore(approvals);
                    bus.register("echo", (c, call) -> SyscallResult.success("ok"));
                    bus.addGuardHook((c, call) -> PolicyVerdict.NEED_APPROVAL);
                    bus.addGuardHook((c, call) -> PolicyVerdict.DENY);
                    expectThrows(com.tepeu.os.bus.BusGuardException.class,
                            () -> bus.invoke(turn(), new Syscall("echo", Map.of())));
                    check(approvals.records().isEmpty(), "DENY 不得登记 asked");
                }));
        cases.add(new ConformanceCase("guard-verdict", "卫兵 ASK + Policy ALLOW 走审批通道",
                () -> {
                    CapabilityBus bus = factory.newBus();
                    ApprovalStore approvals = factory.newApprovalStore();
                    bus.setPolicyHook((c, call) -> PolicyVerdict.ALLOW);
                    bus.setApprovalStore(approvals);
                    bus.register("echo", (c, call) -> SyscallResult.success("ok"));
                    bus.addGuardHook((c, call) -> PolicyVerdict.NEED_APPROVAL);
                    TurnContext ctx = turn();
                    Syscall call = new Syscall("echo", Map.of());
                    ApprovalRequiredException asked = expectThrows(ApprovalRequiredException.class,
                            () -> bus.invoke(ctx, call));
                    approvals.decide(asked.approvalId(), true, "host");
                    check(bus.invoke(ctx, call).ok(), "放行后执行");
                }));
        cases.add(new ConformanceCase("guard-verdict", "卫兵 ALLOW 压不过 Policy DENY",
                () -> {
                    CapabilityBus bus = factory.newBus();
                    bus.setPolicyHook((c, call) -> PolicyVerdict.DENY);
                    bus.register("echo", (c, call) -> SyscallResult.success("ok"));
                    bus.addGuardHook((c, call) -> PolicyVerdict.ALLOW);
                    PolicyDeniedException ex = expectThrows(PolicyDeniedException.class,
                            () -> bus.invoke(turn(), new Syscall("echo", Map.of())));
                    checkEquals(PolicyVerdict.DENY, ex.verdict(), "Policy DENY");
                }));

        return List.copyOf(cases);
    }

    /** 装配恒 ALLOW 的 Policy，隔离出「纯分发」路径。 */
    private static CapabilityBus wiredAllowBus(BusFactory factory) {
        CapabilityBus bus = factory.newBus();
        bus.setPolicyHook((c, call) -> PolicyVerdict.ALLOW);
        return bus;
    }

    private static TurnContext turn() {
        Principal owner = Principal.personal(new PrincipalId("conformance-user"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("conformance-ws"));
        return new TurnContext(owner, ns, new SessionId("conformance-session"), Optional.empty());
    }
}
