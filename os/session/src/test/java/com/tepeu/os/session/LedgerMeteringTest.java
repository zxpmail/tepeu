package com.tepeu.os.session;

import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.session.memory.InMemorySessionStore;
import com.tepeu.os.syscall.Usage;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LedgerMeteringTest {

    @Test
    void unlimitedAlwaysWithin() {
        Session session = newSession();
        session.ledger().record("llm.generate", new Usage(9_999, 1, 0, 0));
        assertTrue(LedgerMetering.unlimited().withinBudget(session));
    }

    @Test
    void zeroCeilingAlwaysOver() {
        Session session = newSession();
        assertFalse(LedgerMetering.tokens(0).withinBudget(session));
    }

    @Test
    void atCeilingIsOver() {
        Session session = newSession();
        session.ledger().record("llm.generate", new Usage(1, 1, 0, 0));
        assertFalse(LedgerMetering.tokens(2).withinBudget(session));
        assertTrue(LedgerMetering.tokens(3).withinBudget(session));
    }

    @Test
    void sumsInclusiveTotalTokens() {
        Session session = newSession();
        session.ledger().record("llm.generate", new Usage(1, 0, 2, 3));
        // totalTokens = 1+2+3+0 = 6
        assertTrue(LedgerMetering.tokens(7).withinBudget(session));
        assertFalse(LedgerMetering.tokens(6).withinBudget(session));
    }

    @Test
    void rejectsNegativeCeiling() {
        assertThrows(IllegalArgumentException.class, () -> LedgerMetering.tokens(-1));
    }

    private static Session newSession() {
        return new InMemorySessionStore().create(
                Principal.personal(new PrincipalId("u")),
                Namespace.ofWorkspace(new WorkspaceId("w")),
                Optional.empty());
    }
}
