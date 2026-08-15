package com.tepeu.controller;

import com.tepeu.agent.hook.HostChannelGuard;
import com.tepeu.security.InstanceTokenService;
import com.tepeu.service.WorkspacePathResolver;
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
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * WebSocket 终端：cmd.exe（工作区目录）+ 宿主 Hook 门禁 + 实例令牌。
 * 关联：WebSocketConfig、HostChannelGuard、WorkspacePathResolver、useTerminal。
 */
@Component
public class TerminalWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TerminalWebSocketHandler.class);
    private static final Charset CMD_CHARSET = Charset.forName("GBK");
    private static final Pattern PROMPT_TAIL = Pattern.compile("(?s).*[A-Za-z]:\\\\[^\\r\\n]*> ?$");

    private final ObjectMapper objectMapper;
    private final HostChannelGuard hostChannelGuard;
    private final InstanceTokenService instanceTokenService;
    private final WorkspacePathResolver pathResolver;
    private final Map<String, Process> activeProcesses = new ConcurrentHashMap<>();
    private final Map<String, Writer> activeWriters = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public TerminalWebSocketHandler(
            ObjectMapper objectMapper,
            HostChannelGuard hostChannelGuard,
            InstanceTokenService instanceTokenService,
            WorkspacePathResolver pathResolver) {
        this.objectMapper = objectMapper;
        this.hostChannelGuard = hostChannelGuard;
        this.instanceTokenService = instanceTokenService;
        this.pathResolver = pathResolver;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String host = session.getRemoteAddress() != null
                ? session.getRemoteAddress().getHostString() : "";
        if (!host.equals("127.0.0.1")
                && !host.equals("0:0:0:0:0:0:0:1")
                && !host.equals("localhost")) {
            sendJson(session, Map.of("type", "error", "message", "未授权的连接来源"));
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (instanceTokenService.isEnabled()) {
            String token = queryParam(session, "token");
            if (!instanceTokenService.matches(token)) {
                sendJson(session, Map.of("type", "error", "message", "缺少或无效的实例令牌"));
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
        }

        try {
            Path cwd = resolveTerminalCwd(queryParam(session, "workspaceId"));
            ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/Q");
            pb.directory(cwd.toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            activeProcesses.put(session.getId(), process);

            Writer writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), CMD_CHARSET));
            activeWriters.put(session.getId(), writer);

            executor.submit(() -> streamOutput(session, process));
            sendJson(session, Map.of("type", "output", "data",
                    "工作目录: " + cwd.toAbsolutePath() + "\r\n"));
        } catch (Exception e) {
            log.error("Failed to start shell for session {}", session.getId(), e);
            sendJson(session, Map.of("type", "error", "message",
                    e.getMessage() != null ? e.getMessage() : "无法启动终端"));
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    /** 解析终端起始目录；无 workspaceId 时用默认工作区根 */
    private Path resolveTerminalCwd(String workspaceId) {
        try {
            return pathResolver.resolveBasePath(workspaceId);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(
                    "无法打开工作区目录：" + (e.getMessage() == null ? "未知错误" : e.getMessage()), e);
        }
    }

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
            sendJson(session, Map.of("type", "error", "message", "终端未就绪"));
            return;
        }

        String payload = message.getPayload().replaceAll("[\r\n]+$", "");
        if (payload.isBlank()) {
            writer.write("\r\n");
            writer.flush();
            return;
        }

        String channelSid = "terminal-" + session.getId();
        String argsJson = "{\"command\":\"" + payload.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
        HostChannelGuard.GateResult gate = hostChannelGuard.check(
                channelSid, "terminal_shell", argsJson, false);
        if (!gate.allowed()) {
            if ("APPROVAL_REQUIRED".equals(gate.code()) && gate.approvalId() != null) {
                Map<String, Object> evt = new LinkedHashMap<>();
                evt.put("type", "approval_required");
                evt.put("approvalId", gate.approvalId());
                evt.put("tool", "terminal_shell");
                evt.put("command", payload);
                evt.put("sessionId", channelSid);
                sendJson(session, evt);
                HostChannelGuard.GateResult after = hostChannelGuard.awaitPending(gate.approvalId());
                if (!after.allowed()) {
                    sendJson(session, Map.of(
                            "type", "error",
                            "message", after.message() == null ? "命令未获批准" : after.message()));
                    return;
                }
            } else {
                sendJson(session, Map.of(
                        "type", "error",
                        "message", gate.message() == null ? "命令被拒绝" : gate.message()));
                return;
            }
        }

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

    private synchronized void sendJson(WebSocketSession session, Map<String, ?> data) {
        if (!session.isOpen()) return;
        try {
            String json = objectMapper.writeValueAsString(data);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("Failed to send JSON to session {}", session.getId(), e);
        }
    }

    private static String queryParam(WebSocketSession session, String name) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        for (String part : uri.getQuery().split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && name.equals(part.substring(0, eq))) {
                return java.net.URLDecoder.decode(part.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
