package com.tepeu.os.compose;

import com.tepeu.os.llm.FakeLlmTransport;
import com.tepeu.os.llm.LlmTransport;
import com.tepeu.os.policy.DefaultRuleMatrix;
import com.tepeu.os.policy.sqlite.SqliteApprovalStore;
import com.tepeu.os.session.LedgerMetering;
import com.tepeu.os.session.Metering;
import com.tepeu.os.session.sqlite.SqliteSessionStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 本机单写者发行接线 — 四端口生产默认落 SQLite WAL。
 * {@code MemoryAssembly} 仅 conformance / 单测；发行禁止默认 {@code InMemoryApprovalStore}。
 * 可选 {@code dir/policy.rules} 覆盖默认矩阵。llm 默认 fake；密钥由调用方注入传输。
 */
public final class SqliteAssembly {

    private SqliteAssembly() {
    }

    public static MemoryAssembly.Wired file(Path dir) {
        return file(dir, new FakeLlmTransport(), LedgerMetering.unlimited());
    }

    public static MemoryAssembly.Wired file(Path dir, LlmTransport transport, Metering metering) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SqliteSessionStore sessions = new SqliteSessionStore(dir.resolve("kernel.sqlite"));
        SqliteApprovalStore approvals = new SqliteApprovalStore(dir.resolve("approvals.sqlite"));
        DefaultRuleMatrix matrix = loadMatrix(dir.resolve("policy.rules"));
        return MemoryAssembly.wire(sessions, approvals, sessions.audit(), transport, metering,
                dir.resolve("workspace"), MemoryAssembly.defaultPolicy(matrix));
    }

    static DefaultRuleMatrix loadMatrix(Path rules) {
        if (rules == null || !Files.isRegularFile(rules)) {
            return DefaultRuleMatrix.defaults();
        }
        try {
            return DefaultRuleMatrix.parse(Files.readString(rules, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
