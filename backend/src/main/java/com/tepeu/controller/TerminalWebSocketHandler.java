package com.tepeu.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * WebSocket 终端处理器：每个会话启动一个 cmd.exe，按整行命令写入 stdin。
 * 关联：WebSocketConfig、前端 useTerminal。
 *
 * <p>输出侧按块读取：完整行立即回传；以 {@code X:\path>} 结尾的提示符也会刷新，
 * 避免 readLine 卡在无换行的提示符上导致界面像“卡住/不执行”。
 */
@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TerminalWebSocketHandler.class);
    /** Windows 简体中文 cmd 常用编码 */
    private static final Charset CMD_CHARSET = Charset.forName("GBK");
    /** cmd 提示符：如 E:\work\tepeu\backend> */
    private static final Pattern PROMPT_TAIL = Pattern.compile("(?s).*[A-Za-z]:\\\\[^\\r\\n]*> ?$");

    private final ObjectMapper objectMapper;
    private final Map<String, Process> activeProcesses = new ConcurrentHashMap<>();
    private final Map<String, Writer> activeWriters = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public TerminalWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String host = session.getRemoteAddress() != null
                ? session.getRemoteAddress().getHostString() : "";
        if (!host.equals("127.0.0.1")
                && !host.equals("0:0:0:0:0:0:0:1")
                && !host.equals("localhost")) {
            sendJson(session, Map.of("type", "error", "message", "Not authorized"));
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/Q");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            activeProcesses.put(session.getId(), process);

            Writer writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), CMD_CHARSET));
            activeWriters.put(session.getId(), writer);

            executor.submit(() -> streamOutput(session, process));
        } catch (Exception e) {
            log.error("Failed to start shell for session {}", session.getId(), e);
            sendJson(session, Map.of("type", "error", "message", "Failed to start shell"));
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    /** 流式读取 cmd 输出并推送到前端 */
    private void streamOutput(WebSocketSession session, Process process) {
        String sessionId = session.getId();
        StringBuilder buf = new StringBuilder();
        char[] tmp = new char[512];
        try (InputStreamReader reader = new InputStreamReader(process.getInputStream(), CMD_CHARSET)) {
            int n;
            while ((n = reader.read(tmp)) != -1) {
                if (!session.isOpen()) break;
                buf.append(tmp, 0, n);
                flushCompleteLines(session, buf);
                flushPromptIfPresent(session, buf);
            }
            if (!buf.isEmpty() && session.isOpen()) {
                sendJson(session, Map.of("type", "output", "data", buf.toString()));
                buf.setLength(0);
            }
        } catch (Exception e) {
            log.debug("Terminal output stream ended for session {}", sessionId);
        }
    }

    /** 把缓冲区里以 \\n 结束的完整行发出去 */
    private void flushCompleteLines(WebSocketSession session, StringBuilder buf) {
        int start = 0;
        for (int i = 0; i < buf.length(); i++) {
            if (buf.charAt(i) != '\n') continue;
            String line = buf.substring(start, i);
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            sendJson(session, Map.of("type", "output", "data", line));
            start = i + 1;
        }
        if (start > 0) {
            buf.delete(0, start);
        }
    }

    /** 无换行的 cmd 提示符也要立刻刷出，否则前端看起来像死机 */
    private void flushPromptIfPresent(WebSocketSession session, StringBuilder buf) {
        if (buf.isEmpty()) return;
        String s = buf.toString();
        if (PROMPT_TAIL.matcher(s).matches()) {
            sendJson(session, Map.of("type", "output", "data", s));
            buf.setLength(0);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Writer writer = activeWriters.get(session.getId());
        if (writer == null) {
            sendJson(session, Map.of("type", "error", "message", "No active shell"));
            return;
        }

        String payload = message.getPayload().replaceAll("[\r\n]+$", "");
        writer.write(payload);
        writer.write("\r\n");
        writer.flush();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Writer writer = activeWriters.remove(session.getId());
        if (writer != null) {
            try {
                writer.close();
            } catch (Exception e) {
                log.debug("Failed to close terminal writer for session {}", session.getId());
            }
        }
        Process process = activeProcesses.remove(session.getId());
        if (process != null) {
            process.destroy();
        }
    }

    /** 向客户端发送 JSON 消息 */
    private synchronized void sendJson(WebSocketSession session, Map<String, ?> data) {
        if (!session.isOpen()) return;
        try {
            String json = objectMapper.writeValueAsString(data);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("Failed to send JSON to session {}", session.getId(), e);
        }
    }
}
