package com.tepeu.os.syscall;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * syscall {@code args} 指纹 — 审批绑定 {@code (session, name, digest)}；键排序后 SHA-256。
 * 空 Map 摘要稳定；不解析业务语义。
 */
public final class ArgDigest {

    private ArgDigest() {
    }

    public static String of(Map<String, String> args) {
        Map<String, String> map = args == null ? Map.of() : args;
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        StringBuilder canonical = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) {
                canonical.append('\n');
            }
            String key = keys.get(i);
            String value = map.get(key);
            canonical.append(key).append('=').append(value == null ? "" : value);
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 required", e);
        }
    }
}
