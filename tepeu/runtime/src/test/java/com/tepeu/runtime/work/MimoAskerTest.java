package com.tepeu.runtime.work;

import com.sun.net.httpserver.HttpServer;
import com.tepeu.runtime.word.Reply;
import com.tepeu.runtime.word.ResultKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 问模型怎么把几行发出去，以及怎么从 cc-switch 认出 Xiaomi MiMo。
 * 不把密钥写进失败正文。
 */
class MimoAskerTest {

    /** 几行原样放进请求。回答里的文字块接成正文。 */
    @Test
    void 几行不改写() throws Exception {
        String json = MimoAsker.requestJson("mimo-v2.6-flash", "总则\n做完了不算");
        assertTrue(json.contains("\"content\":\"总则\\n做完了不算\""));
        assertFalse(json.contains("system"));
        assertEquals("好\n下一行", MimoAsker.textOf("""
                {"type":"message","content":[{"type":"text","text":"好"},{"type":"text","text":"下一行"}]}
                """));

        AtomicReference<String> seen = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            seen.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"type\":\"message\",\"content\":[{\"type\":\"text\",\"text\":\"好\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            MimoAsker asker = new MimoAsker(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "mimo-v2.6-flash",
                    "sk-test",
                    java.net.http.HttpClient.newHttpClient(),
                    Duration.ofSeconds(5));
            Reply reply = asker.ask(List.of("总则", "只回答一个字：好"));
            assertEquals(ResultKind.成功, reply.kind());
            assertEquals("好", reply.body());
            assertTrue(seen.get().contains("总则\\n只回答一个字：好"));
            assertFalse(seen.get().contains("sk-test"));
        } finally {
            server.stop(0);
        }
    }

    /** 上游拒绝时，记录里只有失败代码，没有上游正文。 */
    @Test
    void 失败不带回正文() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", exchange -> {
            byte[] body = "sk-secret-in-error".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            MimoAsker asker = new MimoAsker(
                    "http://127.0.0.1:" + server.getAddress().getPort(),
                    "mimo-v2.6-flash",
                    "sk-test",
                    java.net.http.HttpClient.newHttpClient(),
                    Duration.ofSeconds(5));
            Reply reply = asker.ask(List.of("总则"));
            assertEquals(ResultKind.失败, reply.kind());
            assertEquals("HTTP_401", reply.resultCode());
            assertEquals("", reply.body());
        } finally {
            server.stop(0);
        }
    }

    /** 点名的那一家才算。别的当前供应商不能装成 Xiaomi MiMo。 */
    @Test
    void 只认小米(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("settings.json"), "{\"currentProviderClaude\":\"mimo\"}");
        Path db = dir.resolve("cc-switch.db");
        String url = "jdbc:sqlite:" + db.toAbsolutePath().toString().replace('\\', '/');
        try (Connection connection = DriverManager.getConnection(url);
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE providers (
                      id TEXT, name TEXT, app_type TEXT, is_current INTEGER, settings_config TEXT)
                    """);
            statement.execute("""
                    INSERT INTO providers VALUES (
                      'other', 'Zhipu GLM', 'claude', 1,
                      '{"env":{"ANTHROPIC_AUTH_TOKEN":"sk-other","ANTHROPIC_BASE_URL":"https://example.test","ANTHROPIC_MODEL":"glm"}}')
                    """);
            statement.execute("""
                    INSERT INTO providers VALUES (
                      'mimo', 'Xiaomi MiMo', 'claude', 0,
                      '{"env":{"ANTHROPIC_AUTH_TOKEN":"sk-mimo","ANTHROPIC_BASE_URL":"https://api.xiaomimimo.com/anthropic","ANTHROPIC_MODEL":"mimo-v2.6-flash"}}')
                    """);
        }
        CcSwitch.Supplier supplier = CcSwitch.requireMimo(dir);
        assertEquals("Xiaomi MiMo", supplier.name());
        assertEquals("https://api.xiaomimimo.com/anthropic", supplier.baseUrl());
        assertEquals("mimo-v2.6-flash", supplier.model());
        assertEquals("sk-mimo", supplier.token());

        Files.writeString(dir.resolve("settings.json"), "{\"currentProviderClaude\":\"other\"}");
        IllegalStateException wrong = assertThrows(IllegalStateException.class, () -> CcSwitch.requireMimo(dir));
        assertEquals("当前 Claude 供应商不是 Xiaomi MiMo", wrong.getMessage());
    }
}
