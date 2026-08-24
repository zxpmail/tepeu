package com.tepeu.os.session.conformance;

import com.tepeu.os.conformance.ConformanceCase;
import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.session.ProjectionBus;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.session.SessionProjections;
import com.tepeu.os.session.memory.InMemoryProjectionBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.tepeu.os.conformance.ConformanceCheck.check;
import static com.tepeu.os.conformance.ConformanceCheck.checkEquals;

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
                    ProjectionBus bus = new InMemoryProjectionBus();
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
                    InMemoryProjectionBus bus = new InMemoryProjectionBus(1);
                    AtomicInteger received = new AtomicInteger();
                    String sub = bus.subscribe(session.id(), e -> {
                        received.incrementAndGet();
                        if (received.get() == 1) {
                            bus.publish(session.id(), new com.tepeu.os.session.SessionEvent(
                                    99, SessionEventType.USER_MESSAGE, java.time.Instant.now(), "nested", Map.of()));
                        }
                    });
                    bus.publish(session.id(), new com.tepeu.os.session.SessionEvent(
                            1, SessionEventType.USER_MESSAGE, java.time.Instant.now(), "one", Map.of()));
                    bus.publish(session.id(), new com.tepeu.os.session.SessionEvent(
                            2, SessionEventType.USER_MESSAGE, java.time.Instant.now(), "two", Map.of()));
                    checkEquals(1, received.get(), "嵌套 publish 时 outer 第二条应 drop");
                    bus.unsubscribe(sub);
                }));
        return cases;
    }

    public static Session minimalSession() {
        return new com.tepeu.os.session.memory.InMemorySession(
                new com.tepeu.os.identity.SessionId("proj-session"),
                Principal.personal(new PrincipalId("proj-user")),
                Namespace.ofWorkspace(new WorkspaceId("proj-ws")),
                Optional.empty());
    }
}
