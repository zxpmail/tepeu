package com.tepeu.runtime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 技能脚本沙箱 — 工作区读写边界、越界拒绝、超时强制中断。
 */
class ScriptSandboxTest {

    @TempDir
    Path workspace;

    ScriptSandbox sandbox;

    @BeforeEach
    void setUp() {
        sandbox = new ScriptSandbox(2000);
    }

    @Test
    void demoScript_readsAndWritesInsideWorkspace() throws Exception {
        Files.createDirectories(workspace.resolve("sandbox"));
        Files.writeString(workspace.resolve("sandbox/in.txt"), "ping", StandardCharsets.UTF_8);

        String source;
        try (var in = new ClassPathResource("runtime/demo-skill.js").getInputStream()) {
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        String result = sandbox.eval(workspace, source, null, 5000L);
        assertTrue(result.contains("demo-echo: ping"), "got: " + result);
        assertEquals("demo-echo: ping",
                Files.readString(workspace.resolve("sandbox/out.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void cannotReadOutsideWorkspace_viaTraversal() {
        String source = """
                try {
                  fs.readText('/../outside.txt');
                  var result = 'LEAK';
                } catch (e) {
                  var result = 'BLOCKED';
                }
                """;
        String result = sandbox.eval(workspace, source, null, 3000L);
        assertEquals("BLOCKED", result);
    }

    @Test
    void cannotReachUserHomeViaDeepTraversal() throws Exception {
        // workspace 在 temp 下；深穿越应被拒绝，不能读到 user.home 下任意文件
        Path homeFile = Path.of(System.getProperty("user.home")).resolve(".tepeu-phase17-probe");
        Files.writeString(homeFile, "secret", StandardCharsets.UTF_8);
        try {
            String source = """
                    try {
                      fs.readText('/../../../../../../../../../../.tepeu-phase17-probe');
                      var result = 'LEAK';
                    } catch (e) {
                      var result = 'BLOCKED';
                    }
                    """;
            String result = sandbox.eval(workspace, source, null, 3000L);
            assertEquals("BLOCKED", result);
        } finally {
            Files.deleteIfExists(homeFile);
        }
    }

    @Test
    void timeout_isForced() {
        String source = "while (true) { }";
        ScriptSandbox.ScriptTimeoutException ex = assertThrows(
                ScriptSandbox.ScriptTimeoutException.class,
                () -> sandbox.eval(workspace, source, null, 400L));
        assertTrue(ex.getMessage().contains("超时") || ex.getMessage().contains("取消"));
    }

    @Test
    void hostJavaLookup_isDenied() {
        String source = """
                try {
                  var System = Java.type('java.lang.System');
                  var result = 'LEAK';
                } catch (e) {
                  var result = 'BLOCKED';
                }
                """;
        String result = sandbox.eval(workspace, source, null, 3000L);
        assertEquals("BLOCKED", result);
    }
}
