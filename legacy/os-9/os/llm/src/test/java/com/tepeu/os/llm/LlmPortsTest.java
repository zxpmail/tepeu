package com.tepeu.os.llm;

import com.tepeu.os.conformance.ConformanceCase;
import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.llm.conformance.LlmConformance;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionStore;
import com.tepeu.os.session.memory.InMemorySessionStore;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Optional;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

class LlmPortsTest {

    @TestFactory
    Stream<DynamicTest> llmConformance() {
        return toDynamicTests(LlmConformance.suite(() -> {
            SessionStore store = new InMemorySessionStore();
            Session session = store.create(
                    Principal.personal(new PrincipalId("llm-user")),
                    Namespace.ofWorkspace(new WorkspaceId("llm-ws")),
                    Optional.empty());
            return new LlmConformance.Fixture() {
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
            };
        }));
    }

    private static Stream<DynamicTest> toDynamicTests(Iterable<ConformanceCase> cases) {
        return StreamSupport.stream(cases.spliterator(), false)
                .map(c -> DynamicTest.dynamicTest(c.displayName(), c::run));
    }
}
