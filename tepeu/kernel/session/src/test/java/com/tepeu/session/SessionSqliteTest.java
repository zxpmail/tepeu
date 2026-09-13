package com.tepeu.session;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tepeu.identity.Principal;
import com.tepeu.identity.PrincipalId;
import com.tepeu.identity.WorkspaceId;
import com.tepeu.persist.sqlite.SqlitePersist;
import com.tepeu.session.persist.PersistedSessionStore;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SessionSqliteTest {

    @Test
    void fileSurvivesReopen(@TempDir Path dir) {
        Path file = dir.resolve("s.db");
        Principal owner = new Principal(new PrincipalId("u"));
        WorkspaceId workspace = new WorkspaceId("ws");
        try (SqlitePersist persist = SqlitePersist.open(file)) {
            Session session = new PersistedSessionStore(persist)
                    .open(SessionStore.DEFAULT, owner, workspace);
            session.log().append(SessionEventType.TOOL_CALL, "{}", Map.of("callId", "c1"));
            session.registers().put("loop.state", "IDLE");
        }
        try (SqlitePersist persist = SqlitePersist.open(file)) {
            Session session = new PersistedSessionStore(persist)
                    .open(SessionStore.DEFAULT, owner, workspace);
            assertEquals("{}", session.log().get(1).orElseThrow().body());
            assertEquals("c1", session.log().get(1).orElseThrow().attr("callId").orElseThrow());
            assertEquals("IDLE", session.registers().get("loop.state").orElseThrow());
        }
    }
}
