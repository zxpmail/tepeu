package com.tepeu.runtime.work;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 读本机 cc-switch 里当前的 Claude 供应商。
 * 密钥只留在这里，不写进步骤记录。
 */
final class CcSwitch {

    private static final Pattern CURRENT_CLAUDE = Pattern.compile(
            "\"currentProviderClaude\"\\s*:\\s*\"([^\"]+)\"");

    private CcSwitch() {
    }

    /** 这一家供应商。密钥不出现在别的字段里。 */
    record Supplier(String name, String baseUrl, String model, String token) {
    }

    /** 读本机当前这一家。没有库、没有密钥，就是没有。 */
    static Optional<Supplier> load(Path dir) {
        Path db = dir.resolve("cc-switch.db");
        if (!Files.isRegularFile(db)) {
            return Optional.empty();
        }
        Optional<String> id = currentClaudeId(dir.resolve("settings.json"));
        String url = "jdbc:sqlite:" + db.toAbsolutePath().toString().replace('\\', '/');
        try (Connection connection = DriverManager.getConnection(url)) {
            if (id.isPresent()) {
                Optional<Supplier> byId = fetch(connection, "id = ?", id.get());
                if (byId.isPresent()) {
                    return byId;
                }
            }
            return fetch(connection, "app_type = 'claude' AND is_current = 1", null);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    /** 读本机那一份，必须是 Xiaomi MiMo。 */
    static Supplier requireMimo() {
        return requireMimo(Path.of(System.getProperty("user.home"), ".cc-switch"));
    }

    /** 必须是 Xiaomi MiMo，并且地址、模型、密钥都在。 */
    static Supplier requireMimo(Path dir) {
        Supplier supplier = load(dir).orElseThrow(() -> new IllegalStateException("本机 cc-switch 没有可用的 Claude 供应商"));
        if (!"Xiaomi MiMo".equals(supplier.name())) {
            throw new IllegalStateException("当前 Claude 供应商不是 Xiaomi MiMo");
        }
        if (supplier.baseUrl().isBlank() || supplier.model().isBlank() || supplier.token().isBlank()) {
            throw new IllegalStateException("Xiaomi MiMo 缺少地址、模型或密钥");
        }
        return supplier;
    }

    /** 设置里点名的那一家。点名优先于别的应用的当前项。 */
    private static Optional<String> currentClaudeId(Path settings) {
        if (!Files.isRegularFile(settings)) {
            return Optional.empty();
        }
        try {
            String text = Files.readString(settings, StandardCharsets.UTF_8);
            Matcher matcher = CURRENT_CLAUDE.matcher(text);
            if (matcher.find()) {
                return Optional.of(matcher.group(1));
            }
        } catch (Exception ex) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    /** 取出名字、地址、模型和密钥。没有密钥就不算装上。 */
    private static Optional<Supplier> fetch(Connection connection, String where, String id) throws Exception {
        String sql = """
                SELECT name,
                  coalesce(
                    json_extract(settings_config, '$.env.ANTHROPIC_AUTH_TOKEN'),
                    json_extract(settings_config, '$.env.ANTHROPIC_API_KEY'),
                    ''
                  ) AS token,
                  coalesce(json_extract(settings_config, '$.env.ANTHROPIC_BASE_URL'), '') AS base_url,
                  coalesce(
                    json_extract(settings_config, '$.env.ANTHROPIC_MODEL'),
                    json_extract(settings_config, '$.env.ANTHROPIC_DEFAULT_SONNET_MODEL'),
                    ''
                  ) AS model
                FROM providers WHERE %s LIMIT 1
                """.formatted(where);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (id != null) {
                statement.setString(1, id);
            }
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
                String token = blankToEmpty(rows.getString("token"));
                if (token.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(new Supplier(
                        blankToEmpty(rows.getString("name")),
                        blankToEmpty(rows.getString("base_url")),
                        blankToEmpty(rows.getString("model")),
                        token));
            }
        }
    }

    /** 空白当成没有。 */
    private static String blankToEmpty(String value) {
        return value == null || value.isBlank() ? "" : value.strip();
    }
}
