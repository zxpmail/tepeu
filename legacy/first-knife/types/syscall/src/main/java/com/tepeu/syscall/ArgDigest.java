package com.tepeu.syscall;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * {@code args} 指纹。键排序后，键与值各带长度前缀做规范化，再 SHA-256；
 * 含换行、{@code =} 的键值不与多键混淆。空 Map 摘要稳定。不解析业务语义。
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
            String safeValue = value == null ? "" : value;
            canonical.append(key.length()).append(':').append(key).append('=')
                    .append(safeValue.length()).append(':').append(safeValue);
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
