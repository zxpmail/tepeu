package com.tepeu.os.execution.local;



import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 子进程环境白名单；禁止继承 JVM 的 API key。 */
final class SpawnEnv {

    private static final Set<String> ALLOW = Set.of(
            "PATH", "PATHEXT", "SYSTEMROOT", "WINDIR", "SYSTEMDRIVE",
            "COMSPEC", "TEMP", "TMP", "TMPDIR", "USERPROFILE", "HOME", "LANG", "LC_ALL");

    private SpawnEnv() {
    }

    static Map<String, String> filtered() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : System.getenv().entrySet()) {
            String key = e.getKey();
            if (key == null || e.getValue() == null) {
                continue;
            }
            if (secret(key)) {
                continue;
            }
            if (ALLOW.contains(key.toUpperCase(Locale.ROOT))) {
                out.put(key, e.getValue());
            }
        }
        return out;
    }

    static Map<String, String> unixMinimal() {
        Map<String, String> out = new LinkedHashMap<>();
        String path = System.getenv("PATH");
        out.put("PATH", path == null || path.isBlank() ? "/usr/bin:/bin" : path);
        out.put("HOME", "/workspace");
        out.put("TMPDIR", "/workspace");
        return out;
    }

    static String comspec(Map<String, String> env) {
        for (Map.Entry<String, String> e : env.entrySet()) {
            if ("COMSPEC".equalsIgnoreCase(e.getKey()) && e.getValue() != null && !e.getValue().isBlank()) {
                return e.getValue();
            }
        }
        String root = null;
        for (Map.Entry<String, String> e : env.entrySet()) {
            if ("SYSTEMROOT".equalsIgnoreCase(e.getKey())) {
                root = e.getValue();
                break;
            }
        }
        if (root == null || root.isBlank()) {
            root = "C:\\Windows";
        }
        return root + "\\System32\\cmd.exe";
    }

    static boolean secret(String key) {
        String u = key.toUpperCase(Locale.ROOT);
        return u.contains("API_KEY") || u.contains("SECRET") || u.contains("TOKEN")
                || u.contains("PASSWORD") || u.contains("CREDENTIAL") || u.contains("PRIVATE_KEY");
    }
}

final class SpawnIo {

    private SpawnIo() {
    }

    static String readCapped(Path file, int limit) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = in.readNBytes(limit);
            return new String(buf, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // temp leftover is not a spawn failure
        }
    }
}
