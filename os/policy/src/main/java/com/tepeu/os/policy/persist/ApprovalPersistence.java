package com.tepeu.os.policy.persist;

import com.tepeu.os.persist.Persist;
import com.tepeu.os.policy.ApprovalStore;
import com.tepeu.os.syscall.ArgDigest;
import org.springframework.dao.DataAccessException;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

/**
 * 用 {@link Persist} 落审批。不碰连接 / 关闭。persist 不知道本类。
 */
public final class ApprovalPersistence {

    private ApprovalPersistence() {
    }

    public static ApprovalStore open(Persist persist) {
        return open(persist, Clock.systemUTC());
    }

    public static ApprovalStore open(Persist persist, Clock clock) {
        Objects.requireNonNull(persist, "persist");
        persist.script(ApprovalSchema.DDL);
        try {
            persist.jdbc().execute("ALTER TABLE approvals ADD COLUMN args_digest TEXT NOT NULL DEFAULT '"
                    + ArgDigest.of(Map.of()) + "'");
        } catch (DataAccessException e) {
            String msg = String.valueOf(e.getMessage()).toLowerCase();
            String cause = e.getCause() == null ? "" : String.valueOf(e.getCause().getMessage()).toLowerCase();
            if (!msg.contains("duplicate column") && !cause.contains("duplicate column")) {
                throw e;
            }
        }
        return new PersistedApprovalStore(persist, clock);
    }
}
