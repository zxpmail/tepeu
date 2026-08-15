package com.tepeu.repository;

import com.tepeu.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Verifies {@link MessageRepository}'s JdbcTemplate interaction (SQL + bind args + RowMapper) with a
 * mocked {@link JdbcTemplate} — no real DB. Confirms the save path persists all five columns, that a
 * missing id/createdAt is auto-filled, and that {@code findBySessionId} uses ascending ordering.
 */
@ExtendWith(MockitoExtension.class)
class MessageRepositoryTest {

    @Mock
    private JdbcTemplate jdbc;
    private MessageRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MessageRepository(jdbc);
    }

    @Test
    void save_fillsIdAndCreatedAtAndInsertsAllColumns() {
        Message message = new Message();
        message.setSessionId("sess-1");
        message.setRole("user");
        message.setContent("hello");

        Message saved = repository.save(message);

        assertNotNull(saved.getId(), "id should be auto-generated");
        assertNotNull(saved.getCreatedAt(), "createdAt should be auto-filled");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), eq(saved.getId()), eq("sess-1"), eq("user"), eq("hello"), eq(saved.getCreatedAt()));
        assertTrue(sql.getValue().toLowerCase().startsWith("insert into message"),
                "expected INSERT into message; got: " + sql.getValue());
    }

    @Test
    void save_preservesCallerProvidedId() {
        Message message = new Message();
        message.setId("fixed-id");
        message.setSessionId("sess-1");
        message.setRole("assistant");
        message.setContent("hi");

        repository.save(message);

        verify(jdbc).update(anyString(), eq("fixed-id"), eq("sess-1"), eq("assistant"), eq("hi"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void findBySessionId_usesAscendingOrder() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq("sess-1")))
                .thenReturn(List.of());

        repository.findBySessionId("sess-1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq("sess-1"));
        String lower = sql.getValue().toLowerCase();
        assertTrue(lower.contains("order by created_at asc"),
                "history must be oldest-first for prompt assembly; got: " + sql.getValue());
    }

    @Test
    void deleteBySessionId_issuesDelete() {
        repository.deleteBySessionId("sess-1");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), eq("sess-1"));
        assertTrue(sql.getValue().toLowerCase().contains("delete from message"),
                "expected DELETE from message; got: " + sql.getValue());
    }
}
