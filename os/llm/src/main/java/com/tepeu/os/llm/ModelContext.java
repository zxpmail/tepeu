package com.tepeu.os.llm;

import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEvent;

import java.util.List;
import java.util.Objects;

/**
 * 模型可见视图管道 v1 — surface → derive → normalize 唯一入口（Observation 候选收口）。
 * Gate / PromptAssembly 改「看见什么」应经此管道或等价约束，不得旁路拼 messages。
 */
public final class ModelContext {

    private static volatile ContextShaper shaper = ContextShapers.none();

    private ModelContext() {
    }

    /** compose 发行默认调用；conformance 默认 none。 */
    public static void install(ContextShaper next) {
        shaper = next == null ? ContextShapers.none() : next;
    }

    public static ContextShaper shaper() {
        return shaper;
    }

    public static List<CanonicalTurn> view(List<SessionEvent> surface) {
        return view(surface, shaper);
    }

    public static List<CanonicalTurn> view(List<SessionEvent> surface, ContextShaper activeShaper) {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(activeShaper, "activeShaper");
        List<CanonicalTurn> normalized = SharedNormalizer.normalize(LogDeriver.derive(surface));
        return activeShaper.shape(normalized);
    }

    public static List<SessionEvent> surfaceOf(Session session) {
        Objects.requireNonNull(session, "session");
        return session.logReplace().surface();
    }

    public static List<CanonicalTurn> view(Session session) {
        return view(surfaceOf(session));
    }
}
