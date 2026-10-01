package com.tepeu.conformance;

import com.tepeu.persist.PersistRecord;
import com.tepeu.persist.sqlite.SqlitePersist;
import java.nio.file.Path;
import java.util.Map;

/** 故障注入用子进程。提交 {@value #COUNT} 条后印 READY，然后挂住等强杀。 */
public final class CrashChild {

    public static final String SPACE = "crash";
    public static final int COUNT = 10;

    private CrashChild() {
    }

    public static void main(String[] args) throws Exception {
        try (SqlitePersist persist = SqlitePersist.open(Path.of(args[0]))) {
            for (int i = 0; i < COUNT; i++) {
                persist.append(SPACE, new PersistRecord(String.valueOf(i), Map.of("v", "v-" + i)));
            }
            System.out.println("READY");
            System.out.flush();
            Thread.sleep(Long.MAX_VALUE);
        }
    }
}
