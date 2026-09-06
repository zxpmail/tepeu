package com.tepeu.os.llm.conformance;

import com.tepeu.os.conformance.ConformanceCase;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.llm.local.FakeLlmTransport;
import com.tepeu.os.llm.local.LlmGenerateHandler;
import com.tepeu.os.llm.local.LlmTransports;
import com.tepeu.os.llm.LlmTransport;
import com.tepeu.os.llm.PreparedRequest;
import com.tepeu.os.llm.ProtocolFamily;
import com.tepeu.os.llm.local.LogDeriver;
import com.tepeu.os.llm.local.SharedNormalizer;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.session.SessionStore;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallResult;
import com.tepeu.os.syscall.Usage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.tepeu.os.conformance.ConformanceCheck.check;
import static com.tepeu.os.conformance.ConformanceCheck.checkEquals;

/**
 * llm.* 派生式断言套件（测试）— fake 传输；须过「digest 稳定 / 禁 messages 入参」。不进发行 jar。
 */
public final class LlmConformance {

    public interface Fixture {
        SessionStore store();

        Session session();

        TurnContext turn();
    }

    @FunctionalInterface
    public interface FixtureFactory {
        Fixture create();
    }

    private LlmConformance() {
    }

    public static List<ConformanceCase> suite(FixtureFactory factory) {
        List<ConformanceCase> cases = new ArrayList<>();
        cases.add(new ConformanceCase("prepare", "同一 surface 两次 prepare digest 相等",
                () -> {
                    Session s = factory.create().session();
                    s.log().append(SessionEventType.USER_MESSAGE, "hi", Map.of());
                    PreparedRequest a = LlmTransports.prepare(
                            s.logReplace().surface(), ProtocolFamily.ANTHROPIC, "m", "");
                    PreparedRequest b = LlmTransports.prepare(
                            s.logReplace().surface(), ProtocolFamily.ANTHROPIC, "m", "");
                    checkEquals(a.digest(), b.digest(), "digest 稳定");
                    checkEquals(LogDeriver.VERSION, a.deriveVersion(), "derive 版本");
                    checkEquals(SharedNormalizer.VERSION, a.normalizeVersion(), "normalize 版本");
                }));
        cases.add(new ConformanceCase("handler", "args 携带 messages 结构性拒绝",
                () -> {
                    Fixture f = factory.create();
                    SyscallResult r = handler(f).handle(f.turn(),
                            new Syscall(LlmGenerateHandler.NAME, Map.of("model", "m", "messages", "nope")));
                    check(!r.ok(), "必须失败");
                    checkEquals("STRUCTURAL", r.errorCode().orElse(""), "错误码");
                }));
        cases.add(new ConformanceCase("handler", "缺 model 拒绝",
                () -> {
                    Fixture f = factory.create();
                    SyscallResult r = handler(f).handle(f.turn(), new Syscall(LlmGenerateHandler.NAME, Map.of()));
                    checkEquals("CONFIG", r.errorCode().orElse(""), "错误码");
                }));
        cases.add(new ConformanceCase("handler", "fake 往返落 ledger digest，二次调用复核上笔通过",
                () -> {
                    Fixture f = factory.create();
                    f.session().log().append(SessionEventType.USER_MESSAGE, "q", Map.of());
                    LlmGenerateHandler h = handler(f);
                    SyscallResult r = h.handle(f.turn(),
                            new Syscall(LlmGenerateHandler.NAME, Map.of("model", "fake-model")));
                    check(r.ok(), "fake 应成功: " + r.errorCode().orElse(""));
                    checkEquals(FakeLlmTransport.OUTPUT, r.output(), "输出");
                    checkEquals(1, f.session().ledger().readAll().size(), "落账一笔");
                    check(f.session().ledger().readAll().get(0).attrs().containsKey("digest"), "digest");
                    SyscallResult again = h.handle(f.turn(),
                            new Syscall(LlmGenerateHandler.NAME, Map.of("model", "fake-model")));
                    check(again.ok(), "上笔复核应通过: " + again.errorCode().orElse(""));
                }));
        cases.add(new ConformanceCase("handler", "上笔 digest 被篡改则 ASSERTION，不发传输",
                () -> {
                    Fixture f = factory.create();
                    f.session().ledger().record(LlmGenerateHandler.NAME, new Usage(1, 1, 0, 0), Map.of(
                            "digest", "deadbeef",
                            "throughSeq", "0",
                            "family", "anthropic",
                            "model", "m",
                            "system", ""));
                    CountingTransport counting = new CountingTransport();
                    LlmGenerateHandler guarded = new LlmGenerateHandler(f.store(), counting);
                    SyscallResult r = guarded.handle(f.turn(),
                            new Syscall(LlmGenerateHandler.NAME, Map.of("model", "m")));
                    checkEquals("ASSERTION", r.errorCode().orElse(""), "错误码");
                    checkEquals(0, counting.calls, "不得调用传输");
                }));
        cases.add(new ConformanceCase("handler", "replaceRange 后二次 generate 不 ASSERTION",
                () -> {
                    Fixture f = factory.create();
                    f.session().log().append(SessionEventType.USER_MESSAGE, "a", Map.of());
                    f.session().log().append(SessionEventType.USER_MESSAGE, "b", Map.of());
                    f.session().log().append(SessionEventType.USER_MESSAGE, "c", Map.of());
                    LlmGenerateHandler h = handler(f);
                    SyscallResult first = h.handle(f.turn(),
                            new Syscall(LlmGenerateHandler.NAME, Map.of("model", "fake-model")));
                    check(first.ok(), "首轮应成功: " + first.errorCode().orElse(""));
                    f.session().logReplace().replaceRange(1, 2, "sum");
                    SyscallResult second = h.handle(f.turn(),
                            new Syscall(LlmGenerateHandler.NAME, Map.of("model", "fake-model")));
                    check(second.ok(), "压缩后应成功: " + second.errorCode().orElse("") + " " + second.output());
                    check(!second.errorCode().orElse("").equals("ASSERTION"), "不得 ASSERTION");
                }));
        return List.copyOf(cases);
    }

    private static LlmGenerateHandler handler(Fixture fixture) {
        return new LlmGenerateHandler(fixture.store(), new FakeLlmTransport());
    }

    private static final class CountingTransport implements LlmTransport {
        private int calls;

        @Override
        public Reply complete(PreparedRequest prepared) {
            calls++;
            return new Reply("nope", new Usage(0, 0, 0, 0));
        }
    }
}
