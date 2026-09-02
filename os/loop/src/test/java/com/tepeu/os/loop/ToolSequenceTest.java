package com.tepeu.os.loop;

import com.tepeu.os.loop.local.ToolSequence;
import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEventType;
import com.tepeu.os.session.memory.InMemorySessionStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSequenceTest {

    @Test
    void detectsReadThenSpawn() {
        Session session = session();
        session.log().append(SessionEventType.TOOL_CALL, "execution.fs.read", java.util.Map.of());
        assertTrue(ToolSequence.tripped(session, "execution.proc.spawn"));
    }

    @Test
    void allowsIsolatedSpawn() {
        Session session = session();
        assertFalse(ToolSequence.tripped(session, "execution.proc.spawn"));
    }

    @Test
    void suffixMatcher() {
        assertTrue(ToolSequence.suffixMatches(
                List.of("a", "execution.fs.read", "execution.proc.spawn"),
                List.of("execution.fs.read", "execution.proc.spawn")));
        assertFalse(ToolSequence.suffixMatches(List.of("execution.fs.read"), List.of("execution.fs.read", "x")));
    }

    private static Session session() {
        InMemorySessionStore store = new InMemorySessionStore();
        return store.create(
                Principal.personal(new PrincipalId("seq-user")),
                Namespace.ofWorkspace(new WorkspaceId("seq-ws")),
                Optional.empty());
    }
}
