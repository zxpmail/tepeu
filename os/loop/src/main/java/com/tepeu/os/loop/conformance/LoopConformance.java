package com.tepeu.os.loop.conformance;

import com.tepeu.os.bus.BusGuardException;
import com.tepeu.os.bus.CapabilityBus;
import com.tepeu.os.conformance.ConformanceCase;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.loop.CompletionClaim;
import com.tepeu.os.loop.CompletionGate;
import com.tepeu.os.loop.LoopConfig;
import com.tepeu.os.loop.LoopState;
import com.tepeu.os.loop.SessionLoop;
import com.tepeu.os.loop.ToolDirective;
import com.tepeu.os.loop.TurnOutcome;
import com.tepeu.os.policy.PolicyVerdict;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.session.SessionStore;
import com.tepeu.os.syscall.SyscallHandler;
import com.tepeu.os.syscall.SyscallResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.tepeu.os.conformance.ConformanceCheck.check;
import static com.tepeu.os.conformance.ConformanceCheck.checkEquals;

/**
 * Loop 套件：claim 主路、有界续跑（含工具）、完成证据门、拦截失败不 completed。
 */
public final class LoopConformance {

    public interface Fixture {
        SessionStore store();

        Session session();

        TurnContext turn();

        CapabilityBus bus();
    }

    @FunctionalInterface
    public interface FixtureFactory {
        Fixture create(SyscallHandler llm);
    }

    private LoopConformance() {
    }

    public static List<ConformanceCase> suite(FixtureFactory factory) {
        List<ConformanceCase> cases = new ArrayList<>();
        cases.add(new ConformanceCase("loop", "inbox 空 → EMPTY，不写 log",
                () -> {
                    CountingHandler llm = CountingHandler.ok("x");
                    Fixture f = factory.create(llm);
                    TurnOutcome o = loop(f).run(f.turn(), LoopConfig.of("m"));
                    checkEquals(TurnOutcome.Kind.EMPTY, o.kind(), "kind");
                    check(f.session().log().readAll().isEmpty(), "log 应空");
                    checkEquals(0, llm.calls.get(), "不得调 llm");
                    checkIdle(f);
                }));
        cases.add(new ConformanceCase("loop", "claim → USER + ASSISTANT → COMPLETED",
                () -> {
                    CountingHandler llm = CountingHandler.ok("hello");
                    Fixture f = factory.create(llm);
                    f.session().inbox().enqueue("q", Optional.of("user"));
                    TurnOutcome o = loop(f).run(f.turn(), LoopConfig.of("m"));
                    check(o.completed(), "应完成: " + o.detail());
                    checkEquals(2, f.session().log().readAll().size(), "两条事件");
                    checkEquals(SessionEventType.USER_MESSAGE, f.session().log().readAll().get(0).type(), "USER");
                    checkEquals("q", f.session().log().readAll().get(0).body(), "正文来自 claim");
                    checkEquals(SessionEventType.ASSISTANT_MESSAGE, f.session().log().readAll().get(1).type(),
                            "ASSISTANT");
                    checkEquals("hello", f.session().log().readAll().get(1).body(), "模型输出");
                    checkEquals(1, llm.calls.get(), "一步");
                    check(f.session().inbox().claimNext().isEmpty(), "ack 后 Inbox 空");
                    checkIdle(f);
                }));
        cases.add(new ConformanceCase("loop", "maxSteps=0 → STOPPED，不 claim、不调 llm",
                () -> {
                    CountingHandler llm = CountingHandler.ok("x");
                    Fixture f = factory.create(llm);
                    f.session().inbox().enqueue("q", Optional.empty());
                    TurnOutcome o = loop(f).run(f.turn(), new LoopConfig("m", "anthropic", "", 0));
                    checkEquals(TurnOutcome.Kind.STOPPED, o.kind(), "kind");
                    checkEquals(0, llm.calls.get(), "不得调 llm");
                    check(f.session().log().readAll().isEmpty(), "log 应空");
                    check(f.session().inbox().claimNext().isPresent(), "消息仍在 Inbox");
                    checkIdle(f);
                }));
        cases.add(new ConformanceCase("loop", "llm ok=false → FAILED，有 USER 无 ASSISTANT，不得 completed",
                () -> {
                    CountingHandler llm = CountingHandler.fail("HANDLER_ERROR", "boom");
                    Fixture f = factory.create(llm);
                    f.session().inbox().enqueue("q", Optional.empty());
                    TurnOutcome o = loop(f).run(f.turn(), LoopConfig.of("m"));
                    checkEquals(TurnOutcome.Kind.FAILED, o.kind(), "kind");
                    checkEquals(1, f.session().log().readAll().size(), "仅 USER");
                    checkEquals(SessionEventType.USER_MESSAGE, f.session().log().readAll().get(0).type(), "USER");
                    check(!CompletionGate.allow(f.session(), CompletionClaim.REPLY, 0), "门应拒");
                    checkIdle(f);
                }));
        cases.add(new ConformanceCase("loop", "空白输出 → INCOMPLETE，不写空 ASSISTANT",
                () -> {
                    CountingHandler llm = CountingHandler.ok("   ");
                    Fixture f = factory.create(llm);
                    f.session().inbox().enqueue("q", Optional.empty());
                    TurnOutcome o = loop(f).run(f.turn(), LoopConfig.of("m"));
                    checkEquals(TurnOutcome.Kind.INCOMPLETE, o.kind(), "kind");
                    checkEquals(1, f.session().log().readAll().size(), "仅 USER");
                    checkIdle(f);
                }));
        cases.add(new ConformanceCase("loop", "卫兵拦截 → FAILED，不 completed",
                () -> {
                    CountingHandler llm = CountingHandler.ok("hello");
                    Fixture f = factory.create(llm);
                    f.bus().addGuardHook((ctx, call) -> {
                        throw new BusGuardException("blocked");
                    });
                    f.session().inbox().enqueue("q", Optional.empty());
                    TurnOutcome o = loop(f).run(f.turn(), LoopConfig.of("m"));
                    checkEquals(TurnOutcome.Kind.FAILED, o.kind(), "kind");
                    check(o.detail().contains("blocked"), "细节: " + o.detail());
                    checkEquals(0, llm.calls.get(), "不得进 handler");
                    check(!o.completed(), "不得 completed");
                    checkIdle(f);
                }));
        cases.add(new ConformanceCase("loop", "running 中禁止再 run",
                () -> {
                    Fixture f = factory.create(CountingHandler.ok("x"));
                    f.session().registers().put(SessionLoop.REGISTER_STATE, LoopState.RUNNING.name());
                    TurnOutcome o = loop(f).run(f.turn(), LoopConfig.of("m"));
                    checkEquals(TurnOutcome.Kind.INVALID, o.kind(), "kind");
                    checkEquals(LoopState.RUNNING.name(),
                            f.session().registers().get(SessionLoop.REGISTER_STATE).orElse(""),
                            "不得改别人的 running");
                }));
        cases.add(new ConformanceCase("loop", "maintenance 中禁止 run",
                () -> {
                    Fixture f = factory.create(CountingHandler.ok("x"));
                    f.session().registers().put(SessionLoop.REGISTER_STATE, LoopState.MAINTENANCE.name());
                    TurnOutcome o = loop(f).run(f.turn(), LoopConfig.of("m"));
                    checkEquals(TurnOutcome.Kind.INVALID, o.kind(), "kind");
                }));
        cases.add(new ConformanceCase("gate", "仅 REASONING 不得当答复完成",
                () -> {
                    Fixture f = factory.create(CountingHandler.ok("x"));
                    f.session().log().append(SessionEventType.USER_MESSAGE, "q", Map.of());
                    f.session().log().append(SessionEventType.REASONING, "think", Map.of());
                    check(!CompletionGate.allow(f.session(), CompletionClaim.REPLY, 1), "应拒");
                }));
        cases.add(new ConformanceCase("gate", "TOOL_CALL 无 RESULT 不得完成",
                () -> {
                    Fixture f = factory.create(CountingHandler.ok("x"));
                    f.session().log().append(SessionEventType.TOOL_CALL, "echo", Map.of());
                    check(!CompletionGate.allow(f.session(), CompletionClaim.TOOL_PAIR, 0), "应拒");
                }));
        cases.add(new ConformanceCase("gate", "TOOL_CALL+TOOL_RESULT 成对放行",
                () -> {
                    Fixture f = factory.create(CountingHandler.ok("x"));
                    f.session().log().append(SessionEventType.TOOL_CALL, "echo", Map.of());
                    f.session().log().append(SessionEventType.TOOL_RESULT, "ok", Map.of());
                    check(CompletionGate.allow(f.session(), CompletionClaim.TOOL_PAIR, 0), "应放行");
                }));
        cases.add(new ConformanceCase("directive", "首行 syscall <name> 才是工具，其余当答复",
                () -> {
                    check(ToolDirective.parse("hello").isEmpty(), "普通文本");
                    check(ToolDirective.parse("syscall").isEmpty(), "无名字");
                    check(ToolDirective.parse("syscall echo extra").isEmpty(), "首行多余 token");
                    ToolDirective d = ToolDirective.parse("syscall echo\ntext=hi\n").orElseThrow();
                    checkEquals("echo", d.name(), "name");
                    checkEquals("hi", d.args().get("text"), "arg");
                }));
        cases.add(new ConformanceCase("loop", "工具：CALL→总线→RESULT→再 generate→COMPLETED",
                () -> {
                    CountingHandler llm = CountingHandler.outputs("syscall echo\ntext=hi", "done");
                    Fixture f = factory.create(llm);
                    f.bus().register("echo", (ctx, call) ->
                            SyscallResult.success(call.args().getOrDefault("text", "")));
                    f.session().inbox().enqueue("q", Optional.empty());
                    TurnOutcome o = loop(f).run(f.turn(), LoopConfig.of("m"));
                    check(o.completed(), "应完成: " + o.detail());
                    checkEquals(2, o.steps(), "两步 generate");
                    checkEquals(4, f.session().log().readAll().size(), "USER CALL RESULT ASSISTANT");
                    checkEquals(SessionEventType.USER_MESSAGE, f.session().log().readAll().get(0).type(), "USER");
                    checkEquals(SessionEventType.TOOL_CALL, f.session().log().readAll().get(1).type(), "CALL");
                    checkEquals("echo", f.session().log().readAll().get(1).body(), "CALL body=name");
                    checkEquals("hi", f.session().log().readAll().get(1).attrs().get("text"), "CALL args");
                    checkEquals(SessionEventType.TOOL_RESULT, f.session().log().readAll().get(2).type(), "RESULT");
                    checkEquals("hi", f.session().log().readAll().get(2).body(), "工具输出");
                    checkEquals("true", f.session().log().readAll().get(2).attrs().get("ok"), "ok");
                    checkEquals(SessionEventType.ASSISTANT_MESSAGE, f.session().log().readAll().get(3).type(),
                            "ASSISTANT");
                    checkEquals("done", f.session().log().readAll().get(3).body(), "答复");
                    checkEquals(2, llm.calls.get(), "两次 generate");
                    checkIdle(f);
                }));
        cases.add(new ConformanceCase("loop", "先落 TOOL_CALL 再执行（handler 所见最后一条是 CALL）",
                () -> {
                    CountingHandler llm = CountingHandler.outputs("syscall echo\ntext=hi", "done");
                    Fixture f = factory.create(llm);
                    AtomicInteger seen = new AtomicInteger();
                    f.bus().register("echo", (ctx, call) -> {
                        var last = f.session().log().readAll().getLast();
                        checkEquals(SessionEventType.TOOL_CALL, last.type(), "执行前须已落 CALL");
                        checkEquals("echo", last.body(), "name");
                        checkEquals("hi", last.attrs().get("text"), "args 已在 CALL");
                        seen.incrementAndGet();
                        return SyscallResult.success("pong");
                    });
                    f.session().inbox().enqueue("q", Optional.empty());
                    TurnOutcome o = loop(f).run(f.turn(), LoopConfig.of("m"));
                    check(o.completed(), "应完成: " + o.detail());
                    checkEquals(1, seen.get(), "工具执行一次");
                    checkIdle(f);
                }));
        cases.add(new ConformanceCase("loop", "maxSteps=1 且模型要工具 → STOPPED，CALL/RESULT 成对，不得 completed",
                () -> {
                    CountingHandler llm = CountingHandler.outputs("syscall echo");
                    Fixture f = factory.create(llm);
                    f.bus().register("echo", (ctx, call) -> SyscallResult.success("ok"));
                    f.session().inbox().enqueue("q", Optional.empty());
                    TurnOutcome o = loop(f).run(f.turn(), new LoopConfig("m", "anthropic", "", 1));
                    checkEquals(TurnOutcome.Kind.STOPPED, o.kind(), "kind");
                    checkEquals(1, o.steps(), "一步");
                    checkEquals(3, f.session().log().readAll().size(), "USER CALL RESULT");
                    check(CompletionGate.allow(f.session(), CompletionClaim.TOOL_PAIR, 1), "对应成对");
                    check(!o.completed(), "无 ASSISTANT 不得 completed");
                    checkIdle(f);
                }));
        cases.add(new ConformanceCase("loop", "未注册 syscall → RESULT ok=false 后续跑，不抛穿",
                () -> {
                    CountingHandler llm = CountingHandler.outputs("syscall missing", "sorry");
                    Fixture f = factory.create(llm);
                    f.session().inbox().enqueue("q", Optional.empty());
                    TurnOutcome o = loop(f).run(f.turn(), LoopConfig.of("m"));
                    check(o.completed(), "应完成: " + o.detail());
                    checkEquals(SessionEventType.TOOL_RESULT, f.session().log().readAll().get(2).type(), "RESULT");
                    checkEquals("false", f.session().log().readAll().get(2).attrs().get("ok"), "ok");
                    checkEquals("NOT_FOUND", f.session().log().readAll().get(2).attrs().get("errorCode"), "code");
                    checkEquals("sorry", f.session().log().readAll().get(3).body(), "模型看到错误后答复");
                    checkIdle(f);
                }));
        cases.add(new ConformanceCase("loop", "工具 Policy 拒绝 → 合成 RESULT 后 FAILED，无孤儿 CALL",
                () -> {
                    CountingHandler llm = CountingHandler.outputs("syscall echo");
                    Fixture f = factory.create(llm);
                    f.bus().register("echo", (ctx, call) -> SyscallResult.success("should-not-run"));
                    f.bus().setPolicyHook((ctx, call) ->
                            "echo".equals(call.name()) ? PolicyVerdict.DENY : PolicyVerdict.ALLOW);
                    f.session().inbox().enqueue("q", Optional.empty());
                    TurnOutcome o = loop(f).run(f.turn(), LoopConfig.of("m"));
                    checkEquals(TurnOutcome.Kind.FAILED, o.kind(), "kind");
                    checkEquals(3, f.session().log().readAll().size(), "USER CALL RESULT");
                    checkEquals(SessionEventType.TOOL_RESULT, f.session().log().readAll().get(2).type(), "RESULT");
                    checkEquals("false", f.session().log().readAll().get(2).attrs().get("ok"), "ok");
                    checkEquals("POLICY", f.session().log().readAll().get(2).attrs().get("errorCode"), "code");
                    check(CompletionGate.allow(f.session(), CompletionClaim.TOOL_PAIR, 1), "对应成对");
                    check(!o.completed(), "不得 completed");
                    checkEquals(1, llm.calls.get(), "不得再 generate");
                    checkIdle(f);
                }));
        cases.add(new ConformanceCase("loop", "工具卫兵拦截 → 合成 RESULT 后 FAILED",
                () -> {
                    CountingHandler llm = CountingHandler.outputs("syscall echo");
                    Fixture f = factory.create(llm);
                    f.bus().register("echo", (ctx, call) -> SyscallResult.success("should-not-run"));
                    f.bus().addGuardHook((ctx, call) -> {
                        if ("echo".equals(call.name())) {
                            throw new BusGuardException("no-echo");
                        }
                    });
                    f.session().inbox().enqueue("q", Optional.empty());
                    TurnOutcome o = loop(f).run(f.turn(), LoopConfig.of("m"));
                    checkEquals(TurnOutcome.Kind.FAILED, o.kind(), "kind");
                    check(o.detail().contains("no-echo"), "细节: " + o.detail());
                    checkEquals("GUARD", f.session().log().readAll().get(2).attrs().get("errorCode"), "code");
                    check(!o.completed(), "不得 completed");
                    checkIdle(f);
                }));
        cases.add(new ConformanceCase("loop", "禁止把 llm.generate 当工具再 invoke",
                () -> {
                    CountingHandler llm = CountingHandler.outputs("syscall llm.generate");
                    Fixture f = factory.create(llm);
                    f.session().inbox().enqueue("q", Optional.empty());
                    TurnOutcome o = loop(f).run(f.turn(), LoopConfig.of("m"));
                    checkEquals(TurnOutcome.Kind.FAILED, o.kind(), "kind");
                    checkEquals(3, f.session().log().readAll().size(), "USER CALL RESULT");
                    checkEquals("STRUCTURAL", f.session().log().readAll().get(2).attrs().get("errorCode"), "code");
                    checkEquals(1, llm.calls.get(), "不得二次 generate");
                    checkIdle(f);
                }));
        return List.copyOf(cases);
    }

    private static SessionLoop loop(Fixture f) {
        return new SessionLoop(f.store(), f.bus());
    }

    private static void checkIdle(Fixture f) {
        String state = f.session().registers().get(SessionLoop.REGISTER_STATE).orElse(LoopState.IDLE.name());
        checkEquals(LoopState.IDLE.name(), state, "finally 须回到 idle");
    }

    static final class CountingHandler implements SyscallHandler {
        final AtomicInteger calls = new AtomicInteger();
        private final List<SyscallResult> script;
        private final boolean repeat;

        private CountingHandler(List<SyscallResult> script, boolean repeat) {
            this.script = List.copyOf(script);
            this.repeat = repeat;
        }

        static CountingHandler ok(String output) {
            return new CountingHandler(List.of(SyscallResult.success(output)), true);
        }

        static CountingHandler fail(String code, String message) {
            return new CountingHandler(List.of(SyscallResult.failure(code, message)), true);
        }

        static CountingHandler outputs(String... outputs) {
            List<SyscallResult> list = new ArrayList<>(outputs.length);
            for (String output : outputs) {
                list.add(SyscallResult.success(output));
            }
            return new CountingHandler(list, false);
        }

        @Override
        public SyscallResult handle(TurnContext ctx, com.tepeu.os.syscall.Syscall syscall) {
            int n = calls.getAndIncrement();
            if (n < script.size()) {
                return script.get(n);
            }
            if (repeat) {
                return script.get(script.size() - 1);
            }
            return SyscallResult.failure("EXHAUSTED", "script ended at " + n);
        }
    }
}
