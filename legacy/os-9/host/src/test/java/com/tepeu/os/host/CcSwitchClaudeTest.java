package com.tepeu.os.host;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CcSwitchClaudeTest {

    @TempDir
    Path dir;

    @Test
    void picksCurrentProviderClaudeNotOtherAppCurrent() throws Exception {
        Files.writeString(dir.resolve("settings.json"), """
                {"currentProviderClaude":"claude-current"}
                """, StandardCharsets.UTF_8);
        Path db = dir.resolve("cc-switch.db");
        String url = "jdbc:sqlite:" + db.toAbsolutePath().toString().replace('\\', '/');
        try (Connection c = DriverManager.getConnection(url);
                Statement s = c.createStatement()) {
            s.execute("""
                    CREATE TABLE providers (
                      id TEXT, name TEXT, app_type TEXT, is_current INTEGER, settings_config TEXT)
                    """);
            s.execute("""
                    INSERT INTO providers VALUES (
                      'codex-current', 'codex', 'codex', 1, '{"env":{"OPENAI_API_KEY":"sk-codex"}}')
                    """);
            s.execute("""
                    INSERT INTO providers VALUES (
                      'claude-current', 'Zhipu', 'claude', 1,
                      '{"env":{"ANTHROPIC_AUTH_TOKEN":"sk-glm","ANTHROPIC_BASE_URL":"https://open.bigmodel.cn/api/anthropic","ANTHROPIC_MODEL":"glm-5.3-flash"}}')
                    """);
        }
        CcSwitchClaude cc = CcSwitchClaude.load(dir).orElseThrow();
        assertEquals("Zhipu", cc.name());
        assertEquals("sk-glm", cc.apiKey());
        assertEquals("https://open.bigmodel.cn/api/anthropic", cc.baseUrl());
        assertEquals("glm-5.3-flash", cc.model());
    }

    @Test
    void missingDirIsEmpty() {
        assertTrue(CcSwitchClaude.load(dir.resolve("missing")).isEmpty());
    }
}
