package com.tepeu.os.observation;

import com.tepeu.os.observation.local.ContextShapers;
import com.tepeu.os.observation.local.LogDeriver;
import com.tepeu.os.observation.local.SharedNormalizer;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEvent;

import java.util.List;
import java.util.Objects;

/**
 * 会话上下文入口：模型可见管道（derive ∘ normalize ∘ shape）。
 * <p>
 * 存面在 {@code session.surface}；压缩经 loop 的日志替换端口；
 * 本组件只读 surface，不持久化，不是第四 store。
 * PromptAssembly（system 静/动段）仍独立，不经此管道。
 * Gate 改「看见什么」须经 {@link #view}，不得旁路拼 messages。
 */
public final class Observation {

    private static volatile ContextShaper shaper = ContextShapers.none();

    private Observation() {
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
