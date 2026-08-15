package com.tepeu.agent;

import com.tepeu.agent.tool.FileTools;
import com.tepeu.agent.tool.ShellTools;
import com.tepeu.agent.tool.ToolEventEmitter;
import com.tepeu.model.Message;
import com.tepeu.service.SkillService;
import com.tepeu.service.chat.ChatService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a chat turn from persisted history and streams the model's reply, with tools.
 * Supports optional fileRefs injection and /@ skill invocation as SystemMessage.
 *
 * <p>调用路径（给 Agent / 人类快速阅读）：同目录 {@code AGENT_CALL_PATH.md}。
 * 深度：Controller → 本类 → ChatService（≤3）。工具清单不在本类，在 {@code Tools.java}。
 */
@Component
public class AgentOrchestrator {

    /** Cap on how many prior turns feed the prompt. Bounds token cost. */
    public static final int MAX_HISTORY_MESSAGES = 50;
    /** 每个 @ 引用文件注入上下文的最大字符数 */
    private static final int MAX_FILE_REF_CHARS = 4000;

    private final ChatService chatService;
    private final FileTools fileTools;
    private final ShellTools shellTools;
    private final SkillService skillService;

    public AgentOrchestrator(ChatService chatService, FileTools fileTools, ShellTools shellTools,
                             SkillService skillService) {
        this.chatService = chatService;
        this.fileTools = fileTools;
        this.shellTools = shellTools;
        this.skillService = skillService;
    }

    public Flux<ChatResponse> streamTurn(String providerId, List<Message> history) {
        return streamTurn(providerId, history, ToolEventEmitter.NOOP, null, null, null);
    }

    public Flux<ChatResponse> streamTurn(String providerId, List<Message> history, ToolEventEmitter emitter) {
        return streamTurn(providerId, history, emitter, null, null, null);
    }

    /**
     * 流式一轮对话；fileRefs 非空时把文件内容前缀注入最后一条 user 消息。
     * workspaceId 用于绑定工具目录；用户消息中的 /技能、@技能 触发本轮技能注入。
     */
    public Flux<ChatResponse> streamTurn(
            String providerId,
            List<Message> history,
            ToolEventEmitter emitter,
            List<String> fileRefs) {
        return streamTurn(providerId, history, emitter, fileRefs, null, null);
    }

    public Flux<ChatResponse> streamTurn(
            String providerId,
            List<Message> history,
            ToolEventEmitter emitter,
            List<String> fileRefs,
            String workspaceId) {
        return streamTurn(providerId, history, emitter, fileRefs, workspaceId, null);
    }

    public Flux<ChatResponse> streamTurn(
            String providerId,
            List<Message> history,
            ToolEventEmitter emitter,
            List<String> fileRefs,
            String workspaceId,
            List<String> skillRefs) {
        fileTools.bindWorkspace(workspaceId);
        shellTools.bindWorkspace(workspaceId);
        try {
            List<org.springframework.ai.chat.messages.Message> promptMessages =
                    toPromptMessages(history, fileRefs, workspaceId, skillRefs);
            Prompt prompt = new Prompt(promptMessages);
            return chatService.streamWithTools(providerId, prompt, emitter)
                    .doFinally(signal -> {
                        fileTools.unbindWorkspace();
                        shellTools.unbindWorkspace();
                    });
        } catch (RuntimeException e) {
            fileTools.unbindWorkspace();
            shellTools.unbindWorkspace();
            throw e;
        }
    }

    /**
     * 组装 Prompt：先注入本轮调用的技能 SystemMessage，再拼历史（跳过历史中的 system 行）。
     */
    private List<org.springframework.ai.chat.messages.Message> toPromptMessages(
            List<Message> history, List<String> fileRefs, String workspaceId, List<String> skillRefs) {
        List<Message> trimmed = history;
        if (history.size() > MAX_HISTORY_MESSAGES) {
            trimmed = history.subList(history.size() - MAX_HISTORY_MESSAGES, history.size());
        }
        String lastUserText = null;
        for (int i = trimmed.size() - 1; i >= 0; i--) {
            if ("user".equals(trimmed.get(i).getRole())) {
                lastUserText = trimmed.get(i).getContent();
                break;
            }
        }
        List<org.springframework.ai.chat.messages.Message> promptMessages = new ArrayList<>();
        skillService.buildInvokedSkillsPrompt(workspaceId, lastUserText, skillRefs).ifPresent(text ->
                promptMessages.add(new SystemMessage(text)));
        List<String> fileOnlyRefs = filterFileRefs(workspaceId, fileRefs);
        for (int i = 0; i < trimmed.size(); i++) {
            Message m = trimmed.get(i);
            boolean lastUser = i == trimmed.size() - 1 && "user".equals(m.getRole());
            switch (m.getRole()) {
                case "user" -> {
                    String content = m.getContent();
                    if (lastUser && !fileOnlyRefs.isEmpty()) {
                        content = buildFileContext(fileOnlyRefs) + content;
                    }
                    promptMessages.add(new UserMessage(content));
                }
                case "assistant" -> promptMessages.add(new AssistantMessage(m.getContent()));
                default -> { /* skip system from history */ }
            }
        }
        return promptMessages;
    }

    /** @ 提及若匹配已安装技能则不当文件路径。 */
    private List<String> filterFileRefs(String workspaceId, List<String> fileRefs) {
        if (fileRefs == null || fileRefs.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String ref : fileRefs) {
            if (ref == null || ref.isBlank()) continue;
            String path = ref.trim();
            if (skillService.isInstalledSkillToken(workspaceId, path)) continue;
            out.add(path);
        }
        return out;
    }

    /** 读取 @ 引用文件并拼成上下文前缀 */
    private String buildFileContext(List<String> fileRefs) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Referenced files]\n");
        for (String ref : fileRefs) {
            if (ref == null || ref.isBlank()) continue;
            String path = ref.trim();
            String body = fileTools.readFile(path);
            if (body.length() > MAX_FILE_REF_CHARS) {
                body = body.substring(0, MAX_FILE_REF_CHARS) + "\n...[truncated]";
            }
            sb.append("--- ").append(path).append(" ---\n")
                    .append(body).append("\n\n");
        }
        sb.append("[User message]\n");
        return sb.toString();
    }
}
