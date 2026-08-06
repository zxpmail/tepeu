package com.tepeu.agent.tool;

import com.tepeu.runtime.ScriptSandbox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** run_skill_script 工具 — demo 与 /scripts 路径约束。 */
class RunSkillScriptToolTest {

    @TempDir
    Path workspace;

    @Test
    void demo_writesSandboxOut() throws Exception {
        RunSkillScriptTool tool = RunSkillScriptTool.forTests(workspace, new ScriptSandbox(5000));
        String out = tool.runSkillScript("demo", "from-input");
        assertTrue(out.contains("demo-echo:"), out);
        assertTrue(Files.isRegularFile(workspace.resolve("sandbox/out.txt")));
        assertTrue(Files.readString(workspace.resolve("sandbox/out.txt"), StandardCharsets.UTF_8)
                .contains("from-input"));
    }

    @Test
    void rejectsPathOutsideScripts() {
        RunSkillScriptTool tool = RunSkillScriptTool.forTests(workspace, new ScriptSandbox(5000));
        String out = tool.runSkillScript("/etc/passwd.js", null);
        assertTrue(out.startsWith("ERROR:"), out);
    }

    @Test
    void runsWorkspaceScriptUnderScripts() throws Exception {
        Path scripts = workspace.resolve("scripts");
        Files.createDirectories(scripts);
        Files.writeString(scripts.resolve("hi.js"),
                "fs.writeText('/scripts/hi.out', 'ok'); var result = 'ok';",
                StandardCharsets.UTF_8);
        RunSkillScriptTool tool = RunSkillScriptTool.forTests(workspace, new ScriptSandbox(5000));
        assertEquals("ok", tool.runSkillScript("/scripts/hi.js", null));
        assertEquals("ok", Files.readString(scripts.resolve("hi.out"), StandardCharsets.UTF_8));
    }
}
