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
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * conformance 桥 — InMemory 实现跑 kernel 随包套件（Pi /testing 四件套模式）。
 * 旧 7 个冒烟测试的语义已全部收编进套件；未来 SQLite 等实现用同一套件验证。
 */
class KernelPortsSmokeTest {

    @TestFactory
    Stream<DynamicTest> sessionConformance() {
        SessionConformance.SessionFactory factory = clock -> newSession(clock);
        return toDynamicTests(SessionConformance.suite(factory));
    }

    @TestFactory
    Stream<DynamicTest> busConformance() {
        return toDynamicTests(BusConformance.suite(InMemoryCapabilityBus::new));
    }

    private static Stream<DynamicTest> toDynamicTests(Iterable<ConformanceCase> cases) {
        return java.util.stream.StreamSupport.stream(cases.spliterator(), false)
                .map(c -> DynamicTest.dynamicTest(c.displayName(), c::run));
    }

    private static Session newSession(Clock clock) {
        Principal owner = Principal.personal(new PrincipalId("conformance-user"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("conformance-ws"));
        return new InMemorySession(new SessionId(UUID.randomUUID().toString()), owner, ns,
                Optional.empty(), clock);
    }
}
