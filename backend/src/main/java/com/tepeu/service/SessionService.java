package com.tepeu.service;

import com.tepeu.model.Message;
import com.tepeu.model.Session;
import com.tepeu.repository.MessageRepository;
import com.tepeu.repository.SessionRepository;
import com.tepeu.repository.TaskRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Manages chat {@link Session}s and the {@link Message}s threaded under them.
 *
 * <p>{@code appendMessage} is the single write path used by both the REST SessionController and the
 * streaming ChatController (user turn before streaming, assistant turn on completion), so the
 * history the orchestrator reads on the next turn stays consistent.
 */
@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final MessageRepository messageRepository;
    private final TaskRepository taskRepository;

    public SessionService(
            SessionRepository sessionRepository,
            MessageRepository messageRepository,
            TaskRepository taskRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.taskRepository = taskRepository;
    }

    public Session createSession(String workspaceId, String title) {
        Session session = new Session();
        session.setWorkspaceId(workspaceId);
        session.setTitle(title);
        return sessionRepository.save(session);
    }

    public List<Session> listSessions(String workspaceId) {
        return sessionRepository.findByWorkspaceId(workspaceId);
    }

    public Optional<Session> getSession(String id) {
        return sessionRepository.findById(id);
    }

    /** Session plus its full message history (oldest first). Empty list if session is new. */
    public Optional<SessionWithMessages> getSessionWithMessages(String id) {
        return sessionRepository.findById(id).map(s ->
                new SessionWithMessages(s, messageRepository.findBySessionId(id)));
    }

    public boolean deleteSession(String id) {
        if (sessionRepository.findById(id).isEmpty()) {
            return false;
        }
        // message rows cascade on the FK, so deleting the session is enough.
        sessionRepository.deleteById(id);
        return true;
    }

    /** 重命名会话；不存在返回 empty。空标题会变成「新对话」。 */
    public Optional<Session> renameSession(String id, String title) {
        String normalized = (title == null || title.isBlank()) ? "新对话" : title.trim();
        if (normalized.length() > 80) {
            normalized = normalized.substring(0, 80);
        }
        return sessionRepository.updateTitle(id, normalized);
    }

    public Message appendMessage(String sessionId, String role, String content) {
        Message message = new Message();
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        Message saved = messageRepository.save(message);
        sessionRepository.touchUpdatedAt(sessionId);
        return saved;
    }

    /** All messages for a session in chronological order — used by the orchestrator to build the
     *  prompt history. */
    public List<Message> listMessages(String sessionId) {
        return messageRepository.findBySessionId(sessionId);
    }

    /**
     * 从指定消息处分叉：复制 0..messageId（含）的消息到新会话，标题追加「 (分支)」。
     *
     * @throws IllegalArgumentException 会话不存在或 messageId 不在该会话消息列表中
     */
    public SessionWithMessages forkFromMessage(String sessionId, String messageId) {
        Session source = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        List<Message> history = messageRepository.findBySessionId(sessionId);

        int index = -1;
        for (int i = 0; i < history.size(); i++) {
            if (messageId.equals(history.get(i).getId())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            throw new IllegalArgumentException("Message not found in session: " + messageId);
        }

        String baseTitle = source.getTitle() == null ? "" : source.getTitle();
        Session fork = new Session();
        fork.setWorkspaceId(source.getWorkspaceId());
        fork.setTitle(baseTitle + " (分支)");
        fork.setParentSessionId(source.getId());
        fork.setForkFromMessageId(messageId);
        Session saved = sessionRepository.save(fork);

        List<Message> copied = new ArrayList<>();
        for (int i = 0; i <= index; i++) {
            Message src = history.get(i);
            Message copy = new Message();
            copy.setSessionId(saved.getId());
            copy.setRole(src.getRole());
            copy.setContent(src.getContent());
            copied.add(messageRepository.save(copy));
        }
        return new SessionWithMessages(saved, copied);
    }

    /** 委托 TaskRepository 汇总会话 token/费用/回合数。 */
    public TaskRepository.SessionTokenStats getStats(String sessionId) {
        return taskRepository.findSessionStats(sessionId);
    }

    /** Session + its message history, returned by {@code GET /api/session/{id}}. */
    public record SessionWithMessages(Session session, List<Message> messages) {}
}
