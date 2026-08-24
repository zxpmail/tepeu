package com.tepeu.os.loop;

import com.tepeu.os.session.Session;
import com.tepeu.os.session.SessionEvent;
import com.tepeu.os.session.SessionEventType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 跨工具 TOOL_CALL 序列指纹 — 命中禁止后缀则熔断（Gate v1，与 {@link DoomLoop} 正交）。
 */
public final class ToolSequence {

    public static final String ERROR_CODE = "SEQUENCE";

    /** 禁止后缀（syscall 名）；读/写后 spawn、连续 double-spawn。 */
    private static final List<List<String>> BANNED_SUFFIXES = List.of(
            List.of("execution.fs.read", "execution.proc.spawn"),
            List.of("execution.fs.write", "execution.proc.spawn"),
            List.of("execution.proc.spawn", "execution.proc.spawn"));

    private ToolSequence() {
    }

    public static boolean tripped(Session session, String incomingName) {
        Objects.requireNonNull(session, "session");
        if (incomingName == null || incomingName.isBlank()) {
            return false;
        }
        if (SessionLoop.SYSCALL_GENERATE.equals(incomingName)) {
            return false;
        }
        List<String> names = toolCallNames(session);
        if (names.isEmpty()) {
            return false;
        }
        if (!incomingName.equals(names.get(names.size() - 1))) {
            names = new ArrayList<>(names);
            names.add(incomingName);
        }
        for (List<String> banned : BANNED_SUFFIXES) {
            if (suffixMatches(names, banned)) {
                return true;
            }
        }
        return false;
    }

    public static String message(List<String> banned) {
        return ERROR_CODE + ": forbidden tool sequence " + String.join(" → ", banned);
    }

    static List<String> toolCallNames(Session session) {
        List<String> names = new ArrayList<>();
        for (SessionEvent event : session.log().readAll()) {
            if (event.type() == SessionEventType.TOOL_CALL) {
                names.add(event.body());
            }
        }
        return names;
    }

    static boolean suffixMatches(List<String> sequence, List<String> suffix) {
        if (sequence.size() < suffix.size()) {
            return false;
        }
        int start = sequence.size() - suffix.size();
        for (int i = 0; i < suffix.size(); i++) {
            if (!suffix.get(i).equals(sequence.get(start + i))) {
                return false;
            }
        }
        return true;
    }

    static List<List<String>> bannedSuffixes() {
        return BANNED_SUFFIXES;
    }
}
