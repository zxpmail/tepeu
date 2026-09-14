package com.tepeu.host;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tepeu.load.Assembly;
import com.tepeu.load.Assembly.Wired;
import com.tepeu.persist.sqlite.SqlitePersist;
import com.tepeu.syscall.SyscallResult;
import com.tepeu.syscall.Usage;
import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplTest {

    /** profile 中立：不 import 具体实现，行为对齐 fake（固定文本，非零用量）。 */
    private static final String FIXED_REPLY = "fake: 收到，固定回复。";

    @TempDir
    Path dir;

    @Test
    void slashLineGoesToCommands() throws Exception {
        String out = run("/help\n");
        assertTrue(out.contains("/approve"));
        assertTrue(out.contains("/btw"));
    }

    @Test
    void plainLineRunsATurnAndPrintsFinalAnswer() throws Exception {
        String out = run("hi\n");
        assertTrue(out.contains(FIXED_REPLY));
    }

    @Test
    void eofExitsWithoutTurn() throws Exception {
        String out = run("");
        assertTrue(out.contains("Tepeu"));
        assertTrue(out.contains(dir.toString()));
    }

    @Test
    void turnWithoutFinalAnswerDoesNotPrintStaleReply() throws Exception {
        java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            Wired wired = Assembly.wire(persist, v -> calls.getAndIncrement() == 0
                            ? com.tepeu.syscall.SyscallResult.success("done")
                            : com.tepeu.syscall.SyscallResult.success("@tool execution.sandbox.probe"),
                    Assembly.SYSTEM_PROMPT, dir);
            new Repl(new BufferedReader(new StringReader("first\nsecond\n")),
                    new PrintStream(bytes, true, StandardCharsets.UTF_8), wired).run();
        }
        String out = bytes.toString(StandardCharsets.UTF_8);
        assertEquals(10, calls.get(), "首轮 1 次生成 + 次轮 8 轮工具后收轮");
        assertEquals(1, out.split("done", -1).length - 1, "旧答复只许印一次");
        assertTrue(out.contains("这轮没有终答"));
    }

    private String run(String input) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (SqlitePersist persist = SqlitePersist.open(dir.resolve("s.db"))) {
            Wired wired = Assembly.wire(persist,
                    visible -> SyscallResult.success(FIXED_REPLY, new Usage(1, 1)),
                    Assembly.SYSTEM_PROMPT, dir);
            new Repl(new BufferedReader(new StringReader(input)),
                    new PrintStream(bytes, true, StandardCharsets.UTF_8), wired).run();
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
