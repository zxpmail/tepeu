package com.tepeu.os.persist.sqlite;

import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.persist.Persist;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlitePersistTest {

    @Test
    void fileOpensSplitDatabasesOnOneEngine() throws Exception {
        Path dir = Files.createTempDirectory("tepeu-persist-engine-");
        try (Persist persist = SqlitePersist.file(dir)) {
            persist.sessions().create(
                    Principal.personal(new PrincipalId("p-user")),
                    Namespace.ofWorkspace(new WorkspaceId("p-ws")),
                    Optional.empty());
            assertNotNull(persist.approvals());
            assertNotNull(persist.audit());
            assertTrue(Files.isRegularFile(dir.resolve(SqlitePersist.KERNEL_FILE)));
            assertTrue(Files.isRegularFile(dir.resolve(SqlitePersist.APPROVALS_FILE)));
        }
    }
}
