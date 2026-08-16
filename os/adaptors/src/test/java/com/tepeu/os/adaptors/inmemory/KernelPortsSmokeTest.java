package com.tepeu.os.adaptors.inmemory;

import com.tepeu.os.kernel.conformance.BusConformance;
import com.tepeu.os.kernel.conformance.ConformanceCase;
import com.tepeu.os.kernel.conformance.SessionConformance;
import com.tepeu.os.kernel.identity.Namespace;
import com.tepeu.os.kernel.identity.Principal;
import com.tepeu.os.kernel.identity.PrincipalId;
import com.tepeu.os.kernel.identity.WorkspaceId;
import com.tepeu.os.kernel.session.Session;
import com.tepeu.os.kernel.session.SessionId;
import com.tepeu.os.kernel.session.SessionStore;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.tepeu.os.kernel.conformance.ConformanceCheck.check;

/**
 * conformance 桥 — InMemory 实现跑 kernel 随包套件（Pi /testing 四件套模式）。
 * 未来 SQLite 等实现用同一套件验证。
 */
class KernelPortsSmokeTest {

    @TestFactory
    Stream<DynamicTest> sessionConformance() {
        SessionConformance.SessionFactory factory = clock -> newSession(clock);
        return toDynamicTests(SessionConformance.suite(factory));
    }

    @TestFactory
    Stream<DynamicTest> busConformance() {
        BusConformance.BusFactory factory = new BusConformance.BusFactory() {
            @Override
            public com.tepeu.os.kernel.bus.CapabilityBus newBus() {
                return new InMemoryCapabilityBus();
            }

            @Override
            public com.tepeu.os.kernel.bus.ApprovalStore newApprovalStore() {
                return new InMemoryApprovalStore();
            }
        };
        return toDynamicTests(BusConformance.suite(factory));
    }

    @TestFactory
    Stream<DynamicTest> sessionStoreRoundTrip() {
        // SessionStore 之名对齐底板 §3.1（原 SessionRegistry）：create→get 往返
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
