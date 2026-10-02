package com.tepeu.runtime.work;

import com.tepeu.runtime.word.Ability;
import com.tepeu.runtime.word.Reply;
import com.tepeu.runtime.word.ResultKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 做事只把结果交回来。
 * 放不放行、写不写步骤记录，是检查口的事。
 */
class WorkTest {

    /** 几行原样发出去。回答里的另一个能力名不会再被调用。 */
    @Test
    void 问模型不改那几行() {
        AtomicReference<List<String>> sent = new AtomicReference<>();
        AskModel ask = new AskModel(lines -> {
            sent.set(lines);
            return Reply.success("去做读东西");
        });
        Reply reply = ask.run(new Attempt("", "", List.of("总则", "这一件")));
        assertEquals(List.of("总则", "这一件"), sent.get());
        assertEquals(ResultKind.成功, reply.kind());
        assertEquals("去做读东西", reply.body());
        assertEquals(Ability.ASK, ask.name());
    }

    /** 读出文件里的字。写进的是要做什么。 */
    @Test
    void 读写文件(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("note.txt");
        Files.writeString(file, "原来的字");
        Reply read = new ReadFile().run(new Attempt(file.toString(), "", List.of()));
        assertEquals("原来的字", read.body());
        Path target = dir.resolve("out.txt");
        Reply write = new WriteFile().run(new Attempt(target.toString(), "要做什么", List.of()));
        assertEquals(ResultKind.成功, write.kind());
        assertEquals("要做什么", Files.readString(target));
    }

    /** 退出码是 0 才算成功。一直不结束就记超时。 */
    @Test
    void 跑命令成功或超时() {
        assertEquals(Duration.ofSeconds(5), RunProgram.WAIT);
        String hostname = System.getenv("SystemRoot") + "\\System32\\hostname.exe";
        Reply ok = new RunProgram().run(new Attempt(hostname, "", List.of()));
        assertEquals(ResultKind.成功, ok.kind());
        assertTrue(!ok.body().isBlank());
        RunProgram limited = new RunProgram(Duration.ofMillis(200));
        Reply late = limited.runCommand(List.of(hostname.replace("hostname.exe", "ping.exe"), "-n", "8", "127.0.0.1"), Duration.ofMillis(200));
        assertEquals(ResultKind.超时, late.kind());
    }
}
