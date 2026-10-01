package com.tepeu.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tepeu.persist.sqlite.SqlitePersist;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 故障注入。子 JVM 已提交的写（未 close、未 checkpoint）在强杀后必须活着：
 * 重开库时 WAL 恢复补账。destroyForcibly 即 kill -9 / TerminateProcess 语义。
 */
class CrashRecoveryTest {

    @TempDir
    Path dir;

    @Test
    void forcedKillKeepsCommittedAppends() throws Exception {
        Path db = dir.resolve("crash.db");
        Process child = new ProcessBuilder(
                "java", "-cp", System.getProperty("java.class.path"),
                CrashChild.class.getName(), db.toString())
                .redirectErrorStream(true)
                .start();
        assertEquals("READY", readReady(child), "子进程没跑到就绪点");
        child.destroyForcibly();
        assertTrue(child.waitFor(30, TimeUnit.SECONDS), "子进程未在期限内退出");

        try (SqlitePersist reopened = SqlitePersist.open(db)) {
            assertEquals(CrashChild.COUNT, reopened.list(CrashChild.SPACE).size());
            assertEquals("v-9", reopened.get(CrashChild.SPACE, "9").orElseThrow().fields().get("v"));
        }
    }

    /** 子进程启动期会往 stderr 打 j.u.l 日志，逐行等到 READY。EOF 即失败。 */
    private static String readReady(Process child) throws Exception {
        try (BufferedReader out = new BufferedReader(
                new InputStreamReader(child.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = out.readLine()) != null) {
                if ("READY".equals(line.trim())) {
                    return "READY";
                }
            }
        }
        return "EOF";
    }
}
