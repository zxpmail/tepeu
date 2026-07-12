package com.tepeu.service;

import com.tepeu.model.Message;
import com.tepeu.model.Session;
import com.tepeu.repository.MessageRepository;
import com.tepeu.repository.SessionRepository;
import com.tepeu.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Logic-level tests for {@link SessionService} with mocked repositories (no DB, no Spring context).
 * Verifies append/list, session-with-messages aggregation, delete semantics, fork, and that
 * user/assistant turns flow through {@code appendMessage} with the right role.
 */
class SessionServiceTest {

    private SessionRepository sessionRepository;
    private MessageRepository messageRepository;
    private TaskRepository taskRepository;
    private SessionService service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(SessionRepository.class);
        messageRepository = mock(MessageRepository.class);
        taskRepository = mock(TaskRepository.class);
        service = new SessionService(sessionRepository, messageRepository, taskRepository);
    }

    @Test
    void createSession_persistsWithWorkspaceAndTitle() {
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            s.setId("sess-1");
            return s;
        });

        Session created = service.createSession("ws-1", "New Chat");

        assertEquals("sess-1", created.getId());
        assertEquals("ws-1", created.getWorkspaceId());
        assertEquals("New Chat", created.getTitle());
        verify(sessionRepository).save(argThat(s -> "ws-1".equals(s.getWorkspaceId())
                && "New Chat".equals(s.getTitle())));
    }

    @Test
    void appendMessage_delegatesToRepositoryWithGivenRole() {
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        Message saved = service.appendMessage("sess-1", "user", "hello");

        assertEquals("sess-1", saved.getSessionId());
        assertEquals("user", saved.getRole());
        assertEquals("hello", saved.getContent());
        verify(messageRepository).save(argThat(m -> "user".equals(m.getRole())
                && "hello".equals(m.getContent()) && "sess-1".equals(m.getSessionId())));
        verify(sessionRepository).touchUpdatedAt("sess-1");
    }

    @Test
    void listMessages_returnsRepositoryResultUnchanged() {
        List<Message> history = List.of(msg("sess-1", "user", "hi"), msg("sess-1", "assistant", "hey"));
        when(messageRepository.findBySessionId("sess-1")).thenReturn(history);

        assertEquals(history, service.listMessages("sess-1"));
    }

    @Test
    void getSessionWithMessages_present_bundlesSessionAndHistory() {
        Session s = session("ws-1", "t");
        s.setId("sess-1");
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(s));
        when(messageRepository.findBySessionId("sess-1"))
                .thenReturn(List.of(msg("sess-1", "user", "q")));

        Optional<SessionService.SessionWithMessages> result = service.getSessionWithMessages("sess-1");

        assertTrue(result.isPresent());
        assertEquals("sess-1", result.get().session().getId());
        assertEquals(1, result.get().messages().size());
    }

    @Test
    void getSessionWithMessages_absent_returnsEmpty() {
        when(sessionRepository.findById("nope")).thenReturn(Optional.empty());
        assertTrue(service.getSessionWithMessages("nope").isEmpty());
        verify(messageRepository, never()).findBySessionId(any());
    }

    @Test
    void deleteSession_present_deletesRow() {
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(session("ws-1", "t")));
        assertTrue(service.deleteSession("sess-1"));
        verify(sessionRepository).deleteById("sess-1");
    }

    @Test
    void deleteSession_absent_returnsFalseAndDoesNotDelete() {
        when(sessionRepository.findById("nope")).thenReturn(Optional.empty());
        assertFalse(service.deleteSession("nope"));
        verify(sessionRepository, never()).deleteById(any());
    }

    @Test
    void forkFromMessage_copiesPrefixAndSetsParentFields() {
        Session source = session("ws-1", "主会话");
        source.setId("sess-1");
        Message m1 = msg("sess-1", "user", "q1");
        m1.setId("msg-1");
        Message m2 = msg("sess-1", "assistant", "a1");
        m2.setId("msg-2");
        Message m3 = msg("sess-1", "user", "q2");
        m3.setId("msg-3");

        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(source));
        when(messageRepository.findBySessionId("sess-1")).thenReturn(List.of(m1, m2, m3));
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> {
            Session s = inv.getArgument(0);
            s.setId("sess-fork");
            return s;
        });
        AtomicInteger copySeq = new AtomicInteger(0);
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> {
            Message m = inv.getArgument(0);
            m.setId("copy-" + copySeq.incrementAndGet());
            return m;
        });

        SessionService.SessionWithMessages result = service.forkFromMessage("sess-1", "msg-2");

        assertEquals("sess-fork", result.session().getId());
        assertEquals("主会话 (分支)", result.session().getTitle());
        assertEquals("sess-1", result.session().getParentSessionId());
        assertEquals("msg-2", result.session().getForkFromMessageId());
        assertEquals(2, result.messages().size());
        verify(messageRepository, times(2)).save(argThat(m -> "sess-fork".equals(m.getSessionId())));
        verify(messageRepository, never()).save(argThat(m -> "q2".equals(m.getContent())));
    }

    @Test
    void forkFromMessage_unknownMessage_throws() {
        Session source = session("ws-1", "t");
        source.setId("sess-1");
        when(sessionRepository.findById("sess-1")).thenReturn(Optional.of(source));
        when(messageRepository.findBySessionId("sess-1"))
                .thenReturn(List.of(msg("sess-1", "user", "hi")));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.forkFromMessage("sess-1", "missing"));
        assertTrue(ex.getMessage().contains("missing"));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void getStats_delegatesToTaskRepository() {
        TaskRepository.SessionTokenStats stats =
                new TaskRepository.SessionTokenStats(100L, 0.02, 3);
        when(taskRepository.findSessionStats("sess-1")).thenReturn(stats);

        assertSame(stats, service.getStats("sess-1"));
        verify(taskRepository).findSessionStats("sess-1");
    }

    // ---- helpers -------------------------------------------------------------------------

    @Test
    void renameSession_updatesTitle() {
        Session s = session("ws-1", "old");
        s.setId("sess-1");
        when(sessionRepository.updateTitle("sess-1", "新名字")).thenReturn(Optional.of(s));

        Optional<Session> result = service.renameSession("sess-1", "新名字");

        assertTrue(result.isPresent());
        verify(sessionRepository).updateTitle("sess-1", "新名字");
    }

    @Test
    void renameSession_blankBecomesDefaultTitle() {
        when(sessionRepository.updateTitle("sess-1", "新对话")).thenReturn(Optional.empty());

        service.renameSession("sess-1", "   ");

        verify(sessionRepository).updateTitle("sess-1", "新对话");
    }

    private static Session session(String workspaceId, String title) {
        Session s = new Session();
        s.setWorkspaceId(workspaceId);
        s.setTitle(title);
        return s;
    }

    private static Message msg(String sessionId, String role, String content) {
        Message m = new Message();
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        return m;
    }
}
