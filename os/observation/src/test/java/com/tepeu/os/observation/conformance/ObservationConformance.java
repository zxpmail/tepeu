package com.tepeu.os.observation.conformance;

import com.tepeu.os.conformance.ConformanceCase;
import com.tepeu.os.observation.CanonicalRole;
import com.tepeu.os.observation.CanonicalTurn;
import com.tepeu.os.observation.ContextShaper;
import com.tepeu.os.observation.local.ContextShapers;
import com.tepeu.os.observation.local.LogDeriver;
import com.tepeu.os.observation.Observation;
import com.tepeu.os.observation.local.RedactingContextShaper;
import com.tepeu.os.observation.local.SharedNormalizer;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEvent;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.session.SessionStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.tepeu.os.conformance.ConformanceCheck.check;
import static com.tepeu.os.conformance.ConformanceCheck.checkEquals;

/**
 * 模型可见管道套件（测试）— derive / normalize / Observation.view。不进发行 jar。
 */
public final class ObservationConformance {

    public interface Fixture {
        SessionStore store();

        Session session();
    }

    @FunctionalInterface
    public interface FixtureFactory {
        Fixture create();
    }

    private ObservationConformance() {
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
        cases.add(new ConformanceCase("context", "replaceRange 后 Observation 只见 checkpoint",
                () -> {
                    Session s = factory.create().session();
                    s.log().append(SessionEventType.USER_MESSAGE, "a", Map.of());
                    s.log().append(SessionEventType.USER_MESSAGE, "b", Map.of());
                    s.log().append(SessionEventType.USER_MESSAGE, "c", Map.of());
                    List<SessionEvent> surface = s.logReplace().surface();
                    SessionEvent from = surface.get(0);
                    SessionEvent to = surface.get(1);
                    s.logReplace().replaceRange(from.seq(), to.seq(), "compacted");
                    var view = Observation.view(s);
                    checkEquals(2, view.size(), "checkpoint + tail");
                    checkEquals(SessionEventType.COMPACTION_CHECKPOINT, view.get(0).source(), "首条为 checkpoint");
                    checkEquals("compacted", view.get(0).body(), "压缩正文");
                    checkEquals("c", view.get(1).body(), "保留尾部");
                }));
        cases.add(new ConformanceCase("context", "ContextShaper redact 不改 USER；TOOL_RESULT 可截断",
                () -> {
                    ContextShaper shaper = ContextShapers.redacting(128);
                    CanonicalTurn user = new CanonicalTurn(
                            CanonicalRole.USER, SessionEventType.USER_MESSAGE, "plain question", Map.of());
                    CanonicalTurn tool = new CanonicalTurn(
                            CanonicalRole.TOOL, SessionEventType.TOOL_RESULT, "y".repeat(200), Map.of());
                    List<CanonicalTurn> shaped = shaper.shape(List.of(user, tool));
                    checkEquals("plain question", shaped.get(0).body(), "USER 不截长度");
                    checkEquals("[REDACTED]", RedactingContextShaper.redactSecrets("sk-abcdefgh12345678"),
                            "密钥打码");
                    check(shaped.get(1).body().endsWith("...[truncated]"), "工具结果截断");
                }));
        return List.copyOf(cases);
    }
}
