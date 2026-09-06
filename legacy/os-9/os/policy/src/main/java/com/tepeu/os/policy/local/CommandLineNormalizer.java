package com.tepeu.os.policy.local;

import com.tepeu.os.policy.*;

import com.tepeu.os.syscall.Syscall;

import java.util.Locale;
import java.util.Objects;

/**
 * execution.proc.spawn 命令行归一化 — Policy 判前先折叠空白、统一小写（Gate 前置 v1）。
 */
public final class CommandLineNormalizer {

    private CommandLineNormalizer() {
    }

    public static String normalize(Syscall syscall) {
        Objects.requireNonNull(syscall, "syscall");
        if (!"execution.proc.spawn".equals(syscall.name())) {
            return "";
        }
        String path = blankToEmpty(syscall.args().get("path"));
        String args = blankToEmpty(syscall.args().get("args"));
        String combined = (path + " " + args).strip();
        if (combined.isEmpty()) {
            return "";
        }
        combined = combined.replace('\\', '/');
        return collapseWhitespace(combined.toLowerCase(Locale.ROOT));
    }

    private static String blankToEmpty(String raw) {
        return raw == null || raw.isBlank() ? "" : raw.strip();
    }

    private static String collapseWhitespace(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean space = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!space && !sb.isEmpty()) {
                    sb.append(' ');
                    space = true;
                }
            } else {
                sb.append(c);
                space = false;
            }
        }
        return sb.toString();
    }
}
