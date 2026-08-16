package com.tepeu.os.kernel.conformance;

import com.tepeu.os.kernel.bus.CapabilityBus;
import com.tepeu.os.kernel.bus.GuardHook;
import com.tepeu.os.kernel.bus.PolicyDeniedException;
import com.tepeu.os.kernel.bus.PolicyVerdict;
import com.tepeu.os.kernel.bus.Syscall;
import com.tepeu.os.kernel.bus.SyscallResult;
import com.tepeu.os.kernel.context.TurnContext;
import com.tepeu.os.kernel.identity.Namespace;
import com.tepeu.os.kernel.identity.Principal;
import com.tepeu.os.kernel.identity.PrincipalId;
import com.tepeu.os.kernel.identity.WorkspaceId;
import com.tepeu.os.kernel.session.SessionId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.tepeu.os.kernel.conformance.ConformanceCheck.check;
import static com.tepeu.os.kernel.conformance.ConformanceCheck.checkEquals;
import static com.tepeu.os.kernel.conformance.ConformanceCheck.expectThrows;

/**
 * 能力总线 conformance 契约 — 任何实现必须全绿。
 *
 * 契约来源：底板 §2（门对称 + 卫兵组合代数 fail-closed）/ §6 红线 /
 * ADR-016 第三轮（封闭 union、异常规范化）+ 代码审计二（失败必达终态）。
 */
public final class BusConformance {

    public interface BusFactory {
        CapabilityBus newBus();
    }

    private BusConformance() {
    }

    public static List<ConformanceCase> suite(BusFactory factory) {
        List<ConformanceCase> cases = new ArrayList<>();

        // ---- dispatch ----
        cases.add(new ConformanceCase("dispatch", "注册→分发往返，结果即处理器输出",
                () -> {
                    CapabilityBus bus = factory.newBus();
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
                    CapabilityBus bus = factory.newBus();
                    SyscallResult r = bus.invoke(turn(), new Syscall("nope", Map.of()));
                    check(!r.ok(), "未注册必须失败可见");
                    checkEquals("NOT_FOUND", r.errorCode().orElse(""), "错误码");
                }));

        // ---- fail-closed（ADR-016 第三/四轮）----
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
                    expectThrows(PolicyDeniedException.class,
                            () -> bus.invoke(turn(), new Syscall("echo", Map.of())));
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
                    CapabilityBus bus = factory.newBus();
                    bus.register("echo", (c, call) -> SyscallResult.success("ok"));
                    bus.addGuardHook(new GuardHook() {
                        @Override
                        public void before(TurnContext c, Syscall call) {
                            throw new IllegalStateException("guard infra broken");
                        }
                    });
                    expectThrows(com.tepeu.os.kernel.bus.BusGuardException.class,
                            () -> bus.invoke(turn(), new Syscall("echo", Map.of())));
                }));
        cases.add(new ConformanceCase("fail-closed", "已取消的 TurnContext 入口即拒",
                () -> {
                    CapabilityBus bus = factory.newBus();
                    bus.register("echo", (c, call) -> SyscallResult.success("ok"));
                    TurnContext cancelled = turn();
                    cancelled.cancel();
                    expectThrows(com.tepeu.os.kernel.bus.BusGuardException.class,
                            () -> bus.invoke(cancelled, new Syscall("echo", Map.of())));
                }));

        // ---- failure visibility（失败必达终态，不伪装成功）----
        cases.add(new ConformanceCase("visibility", "handler 异常转为失败结果，不抛穿总线",
                () -> {
                    CapabilityBus bus = factory.newBus();
                    bus.register("boom", (c, call) -> {
                        throw new IllegalArgumentException("bad");
                    });
                    SyscallResult r = bus.invoke(turn(), new Syscall("boom", Map.of()));
                    check(!r.ok(), "handler 异常必须失败可见");
                    checkEquals("HANDLER_ERROR", r.errorCode().orElse(""), "错误码");
                }));
        cases.add(new ConformanceCase("visibility", "handler 返回 null 同样失败可见",
                () -> {
                    CapabilityBus bus = factory.newBus();
                    bus.register("null", (c, call) -> null);
                    SyscallResult r = bus.invoke(turn(), new Syscall("null", Map.of()));
                    check(!r.ok(), "null 结果必须失败可见");
                }));
        cases.add(new ConformanceCase("visibility", "卫兵 before→handler→after 顺序被观察",
                () -> {
                    CapabilityBus bus = factory.newBus();
                    List<String> trace = new ArrayList<>();
                    bus.addGuardHook(new GuardHook() {
                        @Override
                        public void before(TurnContext c, Syscall call) {
                            trace.add("before");
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
                        public void before(TurnContext c, Syscall call) {
                            throw new com.tepeu.os.kernel.bus.BusGuardException("blocked");
                        }
                    });
                    expectThrows(com.tepeu.os.kernel.bus.BusGuardException.class,
                            () -> bus.invoke(turn(), new Syscall("echo", Map.of())));
                    checkEquals(0, policyCalls.get(), "卫兵中断时 Policy 不应被调用");
                }));

        return List.copyOf(cases);
    }

    private static TurnContext turn() {
        Principal owner = Principal.personal(new PrincipalId("conformance-user"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("conformance-ws"));
        return new TurnContext(owner, ns, new SessionId("conformance-session"), Optional.empty());
    }
}
