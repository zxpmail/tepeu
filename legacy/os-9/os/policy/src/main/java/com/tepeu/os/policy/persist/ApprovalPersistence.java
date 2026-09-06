package com.tepeu.os.policy.persist;

import com.tepeu.os.persist.Persist;
import com.tepeu.os.policy.ApprovalStore;
import com.tepeu.os.syscall.ArgDigest;
import org.springframework.dao.DataAccessException;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.util.Map;
import java.util.Objects;

/**
 * 用 {@link Persist} 落审批。不碰连接 / 关闭。persist 不知道本类。
 * 开库时跑 DDL，并兼容缺 {@code args_digest} 的旧库（重复列只记诊断，其他错误抛出）。
 */
public final class ApprovalPersistence {

    private static final Logger LOG = System.getLogger(ApprovalPersistence.class.getName());
    private static final String COMPONENT = "policy";
    private static final String CLASS_NAME = ApprovalPersistence.class.getSimpleName();

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
            LOG.log(Level.DEBUG, "component={0} class={1} args_digest column already present",
                    COMPONENT, CLASS_NAME);
        }
        LOG.log(Level.INFO, "component={0} class={1} store opened", COMPONENT, CLASS_NAME);
        return new PersistedApprovalStore(persist, clock);
    }
}
