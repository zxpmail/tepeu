package com.tepeu.agent.tool;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 按会话隔离的 {@code run_command} 输出缓存，供 {@link ReadOutputTool} 按偏移续读。
 * 工具执行线程通过 {@link #enter}/{@link #exit} 绑定会话（由 Hook 装饰器调用）。
 */
@Component
public class CommandOutputStore {

    /** 单次缓存上限（字符），大于模型即时回传上限，便于 read_output 续读 */
    public static final int MAX_STORED_CHARS = 256 * 1024;

    /** sessionId 为空时的占位键 */
    static final String ANON_KEY = "_anon";

    public record Snapshot(String command, String output) {}

    private static final ThreadLocal<String> CTX = new ThreadLocal<>();

    private final ConcurrentHashMap<String, Snapshot> bySession = new ConcurrentHashMap<>();

    /** 工具调用线程进入某会话上下文（与 {@link #exit} 成对）。 */
    public static void enter(String sessionId) {
        CTX.set(keyOf(sessionId));
    }

    public static void exit() {
        CTX.remove();
    }

    /** 当前线程会话键；无则 {@link #ANON_KEY}。 */
    public static String currentKey() {
        String k = CTX.get();
        return k != null ? k : ANON_KEY;
    }

    static String keyOf(String sessionId) {
        return (sessionId == null || sessionId.isBlank()) ? ANON_KEY : sessionId;
    }

    /** 写入当前线程会话（或显式 sessionId）的最近一次命令输出。 */
    public void put(String sessionId, String command, String output) {
        String cmd = command == null ? "" : command;
        String out = output == null ? "" : output;
        if (out.length() > MAX_STORED_CHARS) {
            out = out.substring(0, MAX_STORED_CHARS)
                    + "\n...[stored output truncated to " + MAX_STORED_CHARS + " chars]";
        }
        String key = sessionId != null ? keyOf(sessionId) : currentKey();
        bySession.put(key, new Snapshot(cmd, out));
    }

    /** 使用当前线程会话键写入。 */
    public void put(String command, String output) {
        put(currentKey(), command, output);
    }

    public Snapshot get(String sessionId) {
        return bySession.get(keyOf(sessionId));
    }

    /** 读取当前线程会话缓存。 */
    public Snapshot get() {
        return bySession.get(currentKey());
    }

    public void clear(String sessionId) {
        bySession.remove(keyOf(sessionId));
    }
}
