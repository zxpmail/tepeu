package com.tepeu.os.compose;

import com.tepeu.os.bus.CapabilityBus;
import com.tepeu.os.bus.conformance.BusConformance;
import com.tepeu.os.bus.memory.InMemoryCapabilityBus;
import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.loop.LoopConfig;
import com.tepeu.os.loop.TurnOutcome;
import com.tepeu.os.persist.sqlite.SqliteDataSources;
import com.tepeu.os.policy.ApprovalStore;
import com.tepeu.os.policy.PolicyVerdict;
import com.tepeu.os.policy.persist.ApprovalPersistence;
import com.tepeu.os.policy.memory.InMemoryApprovalStore;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发行接线：SQLite 默认，禁止 InMemoryApprovalStore；过 BusConformance；重启 recover。
 */
class SqliteAssemblyTest {

    private static final List<AutoCloseable> OPEN = new CopyOnWriteArrayList<>();

    @AfterAll
    static void closeStores() {
        for (AutoCloseable c : OPEN) {
            try {
                c.close();
            } catch (Exception ignored) {
                // Windows WAL
            }
        }
        OPEN.clear();
    }

    private static MemoryAssembly.Wired issue(Path dir) {
        var kernel = SqliteDataSources.access(dir.resolve("kernel.sqlite"));
        var approvals = SqliteDataSources.access(dir.resolve("approvals.sqlite"));
        return SqliteAssembly.file(dir, kernel, approvals);
    }

    @Test
    void issuanceDoesNotDefaultToInMemoryApproval() throws Exception {
        Path dir = Files.createTempDirectory("tepeu-issue-");
        try (MemoryAssembly.Wired wired = issue(dir)) {
            assertFalse(wired.approvals() instanceof InMemoryApprovalStore);
            assertTrue(Files.isRegularFile(dir.resolve("kernel.sqlite")));
            assertTrue(Files.isRegularFile(dir.resolve("approvals.sqlite")));
        }
    }

    @Test
    void policyRulesFileOverridesWriteToAllow() throws Exception {
        Path dir = Files.createTempDirectory("tepeu-issue-rules-");
        Files.writeString(dir.resolve("policy.rules"), "execution.fs.write ALLOW\n");
        try (MemoryAssembly.Wired wired = issue(dir)) {
            Principal owner = Principal.personal(new PrincipalId("rules-user"));
            Namespace ns = Namespace.ofWorkspace(new WorkspaceId("rules-ws"));
            Session session = wired.sessions().create(owner, ns, Optional.empty());
            TurnContext ctx = new TurnContext(owner, ns, session.id(), Optional.empty());
            SyscallResult r = wired.bus().invoke(ctx,
                    new Syscall("execution.fs.write", java.util.Map.of("path", "ok.txt", "content", "z")));
            assertTrue(r.ok(), r.output());
            assertEquals("z", Files.readString(wired.workspace().resolve("ok.txt")));
        }
    }

    @Test
    void loopReplySurvivesProcessRestartAndRecover() throws Exception {
        Path dir = Files.createTempDirectory("tepeu-issue-loop-");
        Principal owner = Principal.personal(new PrincipalId("issue-user"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("issue-ws"));
        String sessionId;
        try (MemoryAssembly.Wired wired = issue(dir)) {
            Session session = wired.sessions().create(owner, ns, Optional.empty());
            sessionId = session.id().value();
            wired.bus().setPolicyHook((ctx, call) -> PolicyVerdict.ALLOW);
            session.inbox().enqueue("hi", Optional.of("user"));
            TurnContext ctx = new TurnContext(owner, ns, session.id(), Optional.empty());
            TurnOutcome o = wired.loop().run(ctx, LoopConfig.of("fake-model"));
            assertTrue(o.completed(), o.detail());
            session.log().append(SessionEventType.TOOL_CALL, "open", java.util.Map.of());
        }
        try (MemoryAssembly.Wired wired = issue(dir)) {
            Session session = wired.sessions().get(new com.tepeu.os.identity.SessionId(sessionId)).orElseThrow();
            assertEquals(3, session.log().readAll().size());
            assertEquals(1, session.recover());
            assertEquals("INTERRUPTED", session.log().readAll().get(3).attrs().get("errorCode"));
        }
    }

    @TestFactory
    Stream<DynamicTest> busConformanceOnSqliteApprovals() {
        BusConformance.BusFactory factory = new BusConformance.BusFactory() {
            @Override
            public CapabilityBus newBus() {
                return new InMemoryCapabilityBus();
            }

            @Override
            public ApprovalStore newApprovalStore() {
                try {
                    Path dir = Files.createTempDirectory("tepeu-issue-bus-");
                    var persist = SqliteDataSources.access(dir.resolve("approvals.sqlite"));
                    OPEN.add(persist);
                    return ApprovalPersistence.open(persist);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        };
        return StreamSupport.stream(BusConformance.suite(factory).spliterator(), false)
                .map(c -> DynamicTest.dynamicTest(c.displayName(), c::run));
    }
}
