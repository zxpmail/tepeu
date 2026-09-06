package com.tepeu.os.loop;

import com.tepeu.os.bus.CapabilityBus;
import com.tepeu.os.bus.local.LocalCapabilityBus;
import com.tepeu.os.conformance.ConformanceCase;
import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.loop.conformance.LoopConformance;
import com.tepeu.os.loop.local.DoomLoopGuardHook;
import com.tepeu.os.loop.local.SequenceGuardHook;
import com.tepeu.os.policy.PolicyVerdict;
import com.tepeu.os.policy.memory.InMemoryApprovalStore;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionStore;
import com.tepeu.os.session.memory.InMemorySessionStore;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

class LoopPortsTest {

    @TestFactory
    Stream<DynamicTest> loopConformance() {
        return toDynamicTests(LoopConformance.suite(llm -> {
            SessionStore store = new InMemorySessionStore();
            Session session = store.create(
                    Principal.personal(new PrincipalId("loop-user")),
                    Namespace.ofWorkspace(new WorkspaceId("loop-ws")),
                    Optional.empty());
            LocalCapabilityBus bus = new LocalCapabilityBus();
            bus.setPolicyHook((ctx, call) -> PolicyVerdict.ALLOW);
            bus.setApprovalStore(new InMemoryApprovalStore());
            bus.addGuardHook(new DoomLoopGuardHook(store));
            bus.addGuardHook(new SequenceGuardHook(store));
            bus.register(SessionLoop.SYSCALL_GENERATE, llm);
            return new LoopConformance.Fixture() {
                @Override
                public SessionStore store() {
                    return store;
                }

                @Override
                public Session session() {
                    return session;
                }

                @Override
                public TurnContext turn() {
                    return new TurnContext(session.owner(), session.namespace(), session.id(), Optional.empty());
                }

                @Override
                public CapabilityBus bus() {
                    return bus;
                }
            };
        }));
    }

    private static Stream<DynamicTest> toDynamicTests(Iterable<ConformanceCase> cases) {
        return StreamSupport.stream(cases.spliterator(), false)
                .map(c -> DynamicTest.dynamicTest(c.displayName(), c::run));
    }
}
