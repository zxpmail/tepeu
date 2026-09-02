package com.tepeu.os.compose;

import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.llm.local.FakeLlmTransport;
import com.tepeu.os.loop.LoopConfig;
import com.tepeu.os.loop.TurnOutcome;
import com.tepeu.os.orchestration.KnowledgeSource;
import com.tepeu.os.orchestration.local.PromptAssembly;
import com.tepeu.os.orchestration.memory.InMemoryKnowledgeSource;
import com.tepeu.os.policy.memory.InMemoryApprovalStore;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.session.local.SessionProjections;
import com.tepeu.os.session.memory.InMemoryAuditSink;
import com.tepeu.os.session.memory.InMemorySessionStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductPathTest {

    @Test
    void turnProjectsToBusAndMemoryHitsOnlyWhenSourced() {
        InMemorySessionStore store = new InMemorySessionStore();
        MemoryAssembly.Wired wired = MemoryAssembly.wire(
                store,
                new InMemoryApprovalStore(),
                new InMemoryAuditSink(),
                new FakeLlmTransport(),
                com.tepeu.os.session.local.LedgerMetering.unlimited(),
                Path.of("."),
                MemoryAssembly.defaultPolicy());
        Session session = store.create(
                Principal.personal(new PrincipalId("demo-user")),
                Namespace.ofWorkspace(new WorkspaceId("demo-ws")),
                Optional.empty());
        List<String> projected = new ArrayList<>();
        wired.projection().subscribe(session.id(), e -> projected.add(e.type().name()));
        session.inbox().enqueue("hi", Optional.empty());
        TurnContext turn = new TurnContext(session.owner(), session.namespace(), session.id(), Optional.empty());
        TurnOutcome outcome = wired.loop().run(turn, LoopConfig.of("demo-model"));
        assertEquals(TurnOutcome.Kind.COMPLETED, outcome.kind(), outcome.detail());
        long cursor = SessionProjections.publishCatchUp(session, wired.projection(), 0);
        assertTrue(cursor > 0);
        assertTrue(projected.contains(SessionEventType.USER_MESSAGE.name()));
        assertTrue(projected.contains(SessionEventType.ASSISTANT_MESSAGE.name()));

        KnowledgeSource source = new InMemoryKnowledgeSource(List.of(
                new KnowledgeSource.Hit("doc:tepeu", "agent os skeleton")));
        assertTrue(source.query(turn, "agent").stream().anyMatch(h -> h.sourceId().equals("doc:tepeu")));
        PromptAssembly prompts = new PromptAssembly();
        prompts.register(PromptAssembly.memoryHits(source.query(turn, "agent")));
        assertTrue(prompts.assemble(500).includedIds().contains("memory_hits"));
    }
}
