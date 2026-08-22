package com.tepeu.os.session.memory;

import com.tepeu.os.conformance.ConformanceCase;
import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.SessionId;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionStore;
import com.tepeu.os.session.conformance.SessionConformance;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.tepeu.os.conformance.ConformanceCheck.check;

/**
 * session 组件 conformance 桥 — 本模块可独立 {@code mvn -pl session test}。
 */
class SessionPortsTest {

    @TestFactory
    Stream<DynamicTest> sessionConformance() {
        SessionConformance.SessionFactory factory = clock -> newSession(clock);
        return toDynamicTests(SessionConformance.suite(factory));
    }

    @TestFactory
    Stream<DynamicTest> sessionStoreRoundTrip() {
        return toDynamicTests(java.util.List.of(new ConformanceCase("store",
                "SessionStore create→get 同一实例、未知 id empty",
                () -> {
                    SessionStore store = new InMemorySessionStore();
                    Principal owner = Principal.personal(new PrincipalId("store-user"));
                    Namespace ns = Namespace.ofWorkspace(new WorkspaceId("store-ws"));
                    Session created = store.create(owner, ns, Optional.empty());
                    check(store.get(created.id()).orElseThrow() == created, "get 应取回同一会话");
                    check(store.get(new SessionId("no-such-session")).isEmpty(), "未知 id 应 empty");
                    check(created.parentId().isEmpty(), "根会话无 parent");
                })));
    }

    private static Stream<DynamicTest> toDynamicTests(Iterable<ConformanceCase> cases) {
        return StreamSupport.stream(cases.spliterator(), false)
                .map(c -> DynamicTest.dynamicTest(c.displayName(), c::run));
    }

    private static Session newSession(Clock clock) {
        Principal owner = Principal.personal(new PrincipalId("conformance-user"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("conformance-ws"));
        return new InMemorySession(new SessionId(UUID.randomUUID().toString()), owner, ns,
                Optional.empty(), clock);
    }
}
