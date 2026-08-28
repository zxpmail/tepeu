package com.tepeu.os.compose;

import com.tepeu.os.llm.FakeLlmTransport;
import com.tepeu.os.llm.LlmTransport;
import com.tepeu.os.persist.Persist;
import com.tepeu.os.policy.PolicyRulesFile;
import com.tepeu.os.session.LedgerMetering;
import com.tepeu.os.session.Metering;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 本机单写者发行接线。只认已打开的 {@link Persist}，不拼文件名、不关库。
 * 路径与开/关由 host 配置 / Boot 生命周期决定。
 */
public final class SqliteAssembly {

    private SqliteAssembly() {
    }

    public static MemoryAssembly.Wired file(Path dir, Persist kernel, Persist approvals) {
        return file(dir, kernel, approvals, new FakeLlmTransport(), LedgerMetering.unlimited());
    }

    public static MemoryAssembly.Wired file(
            Path dir, Persist kernel, Persist approvals, LlmTransport transport, Metering metering) {
        Objects.requireNonNull(kernel, "kernel");
        Objects.requireNonNull(approvals, "approvals");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        PolicyRulesFile rules = loadRules(dir.resolve("policy.rules"));
        return MemoryAssembly.wire(kernel, approvals, transport, metering, dir.resolve("workspace"),
                MemoryAssembly.defaultPolicy(rules));
    }

    static PolicyRulesFile loadRules(Path rules) {
        if (rules == null || !Files.isRegularFile(rules)) {
            return PolicyRulesFile.builtins();
        }
        try {
            return PolicyRulesFile.parse(Files.readString(rules, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
