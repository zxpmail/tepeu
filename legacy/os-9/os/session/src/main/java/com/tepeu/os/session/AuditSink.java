package com.tepeu.os.session;

import java.util.List;
import java.util.Map;

/**
 * 人动手的流水：批准、拒绝这类。
 * <p>
 * 不要写进 {@link SessionLog}。模型没说过的话，不能假装是对话。
 * 以后要导出给企业看，这两本都要带：对话一本，人手一本。
 */
public interface AuditSink {

    /** 记一笔。谁、做了什么、对象是谁。 */
    default long record(String actor, String action, String detail) {
        return record(actor, action, detail, Map.of());
    }

    /** 同上，多几个键值。返回本槽序号。 */
    long record(String actor, String action, String detail, Map<String, String> attrs);

    /** 从头读人手流水。不是对话 transcript。 */
    List<AuditRecord> readAll();
}
