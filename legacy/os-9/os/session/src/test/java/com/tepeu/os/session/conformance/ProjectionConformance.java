package com.tepeu.os.session.conformance;

import com.tepeu.os.conformance.ConformanceCase;
import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.session.local.LocalProjectionBus;
import com.tepeu.os.session.ProjectionBus;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEvent;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.session.local.SessionProjections;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.tepeu.os.conformance.ConformanceCheck.check;
import static com.tepeu.os.conformance.ConformanceCheck.checkEquals;

/**
 * ProjectionBus / SessionProjections conformance — 通知非真相；catch-up 游标 = entry seq。
 */
public final class ProjectionConformance {

    @FunctionalInterface
    public interface SessionFactory {
        Session create();
    }

    private ProjectionConformance() {
    }

    public static List<ConformanceCase> suite(SessionFactory factory) {
        List<ConformanceCase> cases = new ArrayList<>();
        cases.add(new ConformanceCase("projection", "since 游标增量读",
                () -> {
                    Session session = factory.create();
                    long s1 = session.log().append(SessionEventType.USER_MESSAGE, "a", Map.of());
                    long s2 = session.log().append(SessionEventType.ASSISTANT_MESSAGE, "b", Map.of());
                    checkEquals(2, SessionProjections.since(session, 0).size(), "两条");
                    checkEquals("b", SessionProjections.since(session, s1).get(0).body(), "第二条");
                    checkEquals(s2, SessionProjections.since(session, s1).get(0).seq(), "seq");
                }));
        cases.add(new ConformanceCase("projection", "catchUp 推通知且更新游标",
                () -> {
                    Session session = factory.create();
                    ProjectionBus bus = new LocalProjectionBus();
                    List<String> bodies = new ArrayList<>();
                    bus.subscribe(session.id(), e -> bodies.add(e.body()));
                    session.log().append(SessionEventType.USER_MESSAGE, "hi", Map.of());
                    long cursor = SessionProjections.publishCatchUp(session, bus, 0);
                    check(cursor > 0, "cursor");
                    checkEquals(1, bodies.size(), "一条通知");
                    checkEquals("hi", bodies.get(0), "body");
                    session.log().append(SessionEventType.ASSISTANT_MESSAGE, "ok", Map.of());
                    cursor = SessionProjections.publishCatchUp(session, bus, cursor);
                    checkEquals(2, bodies.size(), "第二条");
                    checkEquals("ok", bodies.get(1), "body2");
                }));
        cases.add(new ConformanceCase("projection", "pending 达 cap 则 drop 订阅",
                () -> {
                    Session session = factory.create();
                    LocalProjectionBus bus = new LocalProjectionBus(1);
                    AtomicInteger received = new AtomicInteger();
                    String sub = bus.subscribe(session.id(), e -> {
                        received.incrementAndGet();
                        if (received.get() == 1) {
                            bus.publish(session.id(), event(99, "nested"));
                        }
                    });
                    bus.publish(session.id(), event(1, "one"));
                    bus.publish(session.id(), event(2, "two"));
                    checkEquals(1, received.get(), "嵌套 publish 时 outer 第二条应 drop");
                    bus.unsubscribe(sub);
                }));
        cases.add(new ConformanceCase("projection", "drop 须记诊断",
                () -> {
                    Session session = factory.create();
                    List<String> notes = new ArrayList<>();
                    LocalProjectionBus bus = new LocalProjectionBus(1, notes::add);
                    AtomicInteger received = new AtomicInteger();
                    bus.subscribe(session.id(), e -> {
                        received.incrementAndGet();
                        if (received.get() == 1) {
                            bus.publish(session.id(), event(99, "nested"));
                        }
                    });
                    bus.publish(session.id(), event(1, "one"));
                    bus.publish(session.id(), event(2, "two"));
                    check(notes.stream().anyMatch(n -> n.contains("drop")), "drop 诊断");
                    check(notes.stream().anyMatch(n -> n.contains("component=session")), "含组件");
                    check(notes.stream().anyMatch(n -> n.contains("class=LocalProjectionBus")), "含类");
                    check(notes.stream().anyMatch(n -> n.contains(session.id().value())), "含 session");
                }));
        cases.add(new ConformanceCase("projection", "消费者异常不阻断其他订阅",
                () -> {
                    Session session = factory.create();
                    List<String> notes = new ArrayList<>();
                    List<String> ok = new ArrayList<>();
                    LocalProjectionBus bus = new LocalProjectionBus(64, notes::add);
                    bus.subscribe(session.id(), e -> {
                        throw new RuntimeException("boom");
                    });
                    bus.subscribe(session.id(), e -> ok.add(e.body()));
                    bus.publish(session.id(), event(1, "hi"));
                    checkEquals(1, ok.size(), "另一订阅仍收到");
                    checkEquals("hi", ok.get(0), "body");
                    check(notes.stream().anyMatch(n -> n.contains("consumer failed")), "失败诊断");
                    check(notes.stream().anyMatch(n -> n.contains("component=session")), "含组件");
                    check(notes.stream().anyMatch(n -> n.contains("class=LocalProjectionBus")), "含类");
                }));
        return cases;
    }

    private static SessionEvent event(long seq, String body) {
        return new SessionEvent(seq, SessionEventType.USER_MESSAGE, Instant.now(), body, Map.of());
    }

    public static Session minimalSession() {
        return new com.tepeu.os.session.memory.InMemorySession(
                new com.tepeu.os.identity.SessionId("proj-session"),
                Principal.personal(new PrincipalId("proj-user")),
                Namespace.ofWorkspace(new WorkspaceId("proj-ws")),
                Optional.empty());
    }
}
