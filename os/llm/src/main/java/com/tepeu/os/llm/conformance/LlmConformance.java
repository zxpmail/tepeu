package com.tepeu.os.llm.conformance;

import com.tepeu.os.conformance.ConformanceCase;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.llm.CanonicalRole;
import com.tepeu.os.llm.FakeLlmTransport;
import com.tepeu.os.llm.LlmGenerateHandler;
import com.tepeu.os.llm.LlmTransport;
import com.tepeu.os.llm.LogDeriver;
import com.tepeu.os.llm.PreparedRequest;
import com.tepeu.os.llm.ProtocolFamily;
import com.tepeu.os.llm.SharedNormalizer;
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
 * llm.* 派生式断言套件 — fake 传输；任何 LlmTransport 实现须过「digest 稳定 / 禁 messages 入参」。
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
        cases.add(new ConformanceCase("derive", "模型可见 7 类均可派生；END_SEED 不进 surface",
                () -> {
                    Session s = factory.create().session();
                    int visible = 0;
                    for (SessionEventType type : SessionEventType.values()) {
                        if (type == SessionEventType.END_SEED) {
                            continue;
                        }
                        s.log().append(type, type.name(), Map.of());
                        visible++;
                    }
                    var derived = LogDeriver.derive(s.logReplace().surface());
                    checkEquals(visible, derived.size(), "可见类各一拍");
                    checkEquals(7, derived.size(), "7 类模型可见");
                    checkEquals(CanonicalRole.USER, derived.get(0).role(), "USER_MESSAGE");
                    checkEquals(CanonicalRole.TOOL, derived.get(3).role(), "TOOL_RESULT");
                    checkEquals(CanonicalRole.REASONING, derived.get(4).role(), "REASONING");
                }));
        cases.add(new ConformanceCase("normalize", "连续 USER 合并",
                () -> {
                    Session s = factory.create().session();
                    s.log().append(SessionEventType.USER_MESSAGE, "a", Map.of());
                    s.log().append(SessionEventType.USER_MESSAGE, "b", Map.of());
                    var n = SharedNormalizer.normalize(LogDeriver.derive(s.logReplace().surface()));
                    checkEquals(1, n.size(), "应合并为一条");
                    checkEquals("a\nb", n.get(0).body(), "合并正文");
                }));
        cases.add(new ConformanceCase("prepare", "同一 surface 两次 prepare digest 相等",
                () -> {
                    Session s = factory.create().session();
                    s.log().append(SessionEventType.USER_MESSAGE, "hi", Map.of());
                    PreparedRequest a = LlmTransport.prepare(
                            s.logReplace().surface(), ProtocolFamily.ANTHROPIC, "m", "");
                    PreparedRequest b = LlmTransport.prepare(
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
