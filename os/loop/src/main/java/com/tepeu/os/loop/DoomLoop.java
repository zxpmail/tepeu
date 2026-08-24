package com.tepeu.os.loop;

import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEvent;
import com.tepeu.os.session.SessionEventType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 同工具同输入连续 N 次 → 第三刀经总线卫兵 {@link PolicyVerdict#NEED_APPROVAL}（见 {@link DoomLoopGuardHook}）。
 * 指纹剥时间戳/随机参数键。
 */
public final class DoomLoop {

    public static final int THRESHOLD = 3;
    public static final String ERROR_CODE = "DOOM_LOOP";

    private static final Set<String> STRIP = Set.of(
            "timestamp", "ts", "time", "random", "nonce", "uuid", "requestid", "request_id");

    private DoomLoop() {
    }

    public static String fingerprint(String name, Map<String, String> args) {
        StringBuilder sb = new StringBuilder(name == null ? "" : name);
        if (args == null || args.isEmpty()) {
            return sb.toString();
        }
        List<String> keys = new ArrayList<>(args.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            if (strip(key)) {
                continue;
            }
            sb.append('\u001f').append(key).append('=').append(args.get(key));
        }
        return sb.toString();
    }

    public static boolean tripped(Session session, String name, Map<String, String> args) {
        return tripped(session, name, args, THRESHOLD);
    }

    public static boolean tripped(Session session, String name, Map<String, String> args, int threshold) {
        Objects.requireNonNull(session, "session");
        if (threshold < 1) {
            throw new IllegalArgumentException("threshold < 1");
        }
        String fp = fingerprint(name, args);
        int streak = 0;
        for (SessionEvent event : session.log().readAll()) {
            if (event.type() != SessionEventType.TOOL_CALL) {
                continue;
            }
            String other = fingerprint(event.body(), event.attrs());
            streak = fp.equals(other) ? streak + 1 : 0;
        }
        return streak >= threshold;
    }

    public static String nudge(String name) {
        return ERROR_CODE + ": repeated tool " + name + " with equivalent args; stopping until a human decides";
    }

    private static boolean strip(String key) {
        if (key == null) {
            return false;
        }
        return STRIP.contains(key.toLowerCase(Locale.ROOT));
    }
}
