package com.tepeu.os.llm.conformance;

import com.tepeu.os.conformance.ConformanceCase;
import com.tepeu.os.llm.CanonicalRole;
import com.tepeu.os.llm.CanonicalTurn;
import com.tepeu.os.llm.ContextShaper;
import com.tepeu.os.llm.Observation;
import com.tepeu.os.llm.local.ContextShapers;
import com.tepeu.os.llm.local.LogDeriver;
import com.tepeu.os.llm.local.RedactingContextShaper;
import com.tepeu.os.llm.local.SharedNormalizer;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEvent;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.session.SessionStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.tepeu.os.conformance.ConformanceCheck.check;
import static com.tepeu.os.conformance.ConformanceCheck.checkEquals;

/** Model-visible pipeline suite. Not shipped. */
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
        cases.add(new ConformanceCase("derive", "7 visible types; skip END_SEED",
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
                    checkEquals(visible, derived.size(), "one beat each");
                    checkEquals(7, derived.size(), "7 model-visible");
                    checkEquals(CanonicalRole.USER, derived.get(0).role(), "USER_MESSAGE");
                    checkEquals(CanonicalRole.TOOL, derived.get(3).role(), "TOOL_RESULT");
                    checkEquals(CanonicalRole.REASONING, derived.get(4).role(), "REASONING");
                }));
        cases.add(new ConformanceCase("normalize", "adjacent USER merge",
                () -> {
                    Session s = factory.create().session();
                    s.log().append(SessionEventType.USER_MESSAGE, "a", Map.of());
                    s.log().append(SessionEventType.USER_MESSAGE, "b", Map.of());
                    var n = SharedNormalizer.normalize(LogDeriver.derive(s.logReplace().surface()));
                    checkEquals(1, n.size(), "merged");
                    checkEquals("a\nb", n.get(0).body(), "body");
                }));
        cases.add(new ConformanceCase("context", "replaceRange leaves checkpoint plus tail",
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
                    checkEquals(SessionEventType.COMPACTION_CHECKPOINT, view.get(0).source(), "checkpoint");
                    checkEquals("compacted", view.get(0).body(), "compact body");
                    checkEquals("c", view.get(1).body(), "tail");
                }));
        cases.add(new ConformanceCase("context", "shaper redacts and may truncate tool body",
                () -> {
                    ContextShaper shaper = ContextShapers.redacting(128);
                    CanonicalTurn user = new CanonicalTurn(
                            CanonicalRole.USER, SessionEventType.USER_MESSAGE, "plain question", Map.of());
                    CanonicalTurn tool = new CanonicalTurn(
                            CanonicalRole.TOOL, SessionEventType.TOOL_RESULT, "y".repeat(200), Map.of());
                    List<CanonicalTurn> shaped = shaper.shape(List.of(user, tool));
                    checkEquals("plain question", shaped.get(0).body(), "USER length");
                    checkEquals("[REDACTED]", RedactingContextShaper.redactSecrets("sk-abcdefgh12345678"),
                            "secret");
                    check(shaped.get(1).body().endsWith("...[truncated]"), "tool truncate");
                }));
        return List.copyOf(cases);
    }
}
