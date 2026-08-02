package com.tepeu.repository;

import tools.jackson.databind.ObjectMapper;
import com.tepeu.model.Memory;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MemoryRepository：FTS 表达式、LIKE 回退与 save 双写 FTS。
 */
@ExtendWith(MockitoExtension.class)
class MemoryRepositoryTest {

    @Mock
    private JdbcTemplate jdbc;
    private MemoryRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MemoryRepository(jdbc, new ObjectMapper());
    }

    @Test
    void toFtsMatch_prefixTokens() {
        assertEquals("\"hello\"* \"world\"*", MemoryRepository.toFtsMatch("hello world"));
        assertEquals("\"tepeu\"*", MemoryRepository.toFtsMatch("tepeu"));
    }

    @Test
    void toFtsMatch_stripsSpecialChars() {
        // 特殊字符被空格替换后拆成多个前缀词
        assertEquals("\"a\"* \"b\"*", MemoryRepository.toFtsMatch("a\"b*"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_usesFtsWhenQueryPresent() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any(), anyInt()))
                .thenReturn(List.of());

        repository.search("ws-1", "agent", null, 10, null);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq("ws-1"), eq("\"agent\"*"), eq(10));
        assertTrue(sql.getValue().toLowerCase().contains("memory_fts"),
                "expected FTS join; got: " + sql.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_fallsBackToLikeWhenFtsThrows() {
        when(jdbc.query(contains("memory_fts"), any(RowMapper.class), any(), any(), anyInt()))
                .thenThrow(new RuntimeException("no fts"));
        when(jdbc.query(contains("LIKE"), any(RowMapper.class), any(), any(), anyInt()))
                .thenReturn(List.of());

        repository.search("ws-1", "agent", null, 10, null);

        verify(jdbc, atLeastOnce()).query(contains("LIKE"), any(RowMapper.class), eq("ws-1"), eq("%agent%"), eq(10));
    }

    @Test
    void save_insertsMemoryAndFts() {
        Memory m = new Memory();
        m.setWorkspaceId("ws-1");
        m.setSource("manual");
        m.setContent("hello memory");
        m.setTags(List.of("tag1"));

        Memory saved = repository.save(m);

        assertNotNull(saved.getId());
        verify(jdbc).update(
                startsWith("INSERT INTO memory "),
                eq(saved.getId()), eq("ws-1"), eq("manual"), eq("hello memory"),
                anyString(), any(), any());
        verify(jdbc).update(
                eq("INSERT INTO memory_fts(id, workspace_id, content) VALUES (?, ?, ?)"),
                eq(saved.getId()), eq("ws-1"), eq("hello memory"));
    }
}
