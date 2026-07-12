package com.tepeu.repository;

import com.tepeu.model.FileVersion;
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
 * Verifies {@link FileVersionRepository}'s JdbcTemplate interaction (SQL + bind args + RowMapper)
 * with a mocked {@link JdbcTemplate} — no real DB.
 *
 * <p>Covers:
 * <ul>
 *   <li>{@code save} auto-generates id, auto-increments version_no, inserts all columns.</li>
 *   <li>{@code save} preserves a caller-provided id.</li>
 *   <li>{@code findByFilePath} queries with workspace_id + file_path and orders by version_no DESC.</li>
 *   <li>{@code findById} returns the single matching row.</li>
 *   <li>{@code findById} returns empty for no match.</li>
 *   <li>{@code deleteById} issues DELETE.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class FileVersionRepositoryTest {

    @Mock
    private JdbcTemplate jdbc;
    private FileVersionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new FileVersionRepository(jdbc);
    }

    @Test
    void save_fillsIdAndVersionNoAndCreatedAtAndInsertsAllColumns() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("ws-1"), eq("/notes.txt")))
                .thenReturn(5);

        FileVersion fv = new FileVersion();
        fv.setWorkspaceId("ws-1");
        fv.setFilePath("/notes.txt");
        fv.setContentRef("/tmp/.versions/uuid-1");
        fv.setCreatedBySession("sess-1");

        FileVersion saved = repository.save(fv);

        assertNotNull(saved.getId(), "id should be auto-generated");
        assertEquals(5, saved.getVersionNo(), "version_no should be auto-incremented");
        assertNotNull(saved.getCreatedAt(), "createdAt should be auto-filled");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(),
                eq(saved.getId()), eq("ws-1"), eq("/notes.txt"),
                eq(5), eq("/tmp/.versions/uuid-1"), eq("sess-1"), eq(saved.getCreatedAt()));
        assertTrue(sql.getValue().toLowerCase().startsWith("insert into file_version"),
                "expected INSERT into file_version; got: " + sql.getValue());
    }

    @Test
    void save_preservesCallerProvidedId() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("ws-1"), eq("/notes.txt")))
                .thenReturn(1);

        FileVersion fv = new FileVersion();
        fv.setId("fixed-version-id");
        fv.setWorkspaceId("ws-1");
        fv.setFilePath("/notes.txt");
        fv.setContentRef("/tmp/.versions/uuid-2");

        repository.save(fv);

        verify(jdbc).update(anyString(),
                eq("fixed-version-id"), eq("ws-1"), eq("/notes.txt"),
                eq(1), eq("/tmp/.versions/uuid-2"), isNull(), any());
    }

    @Test
    void save_computesVersionNoFromMaxPlusOne() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq("ws-1"), eq("/notes.txt")))
                .thenReturn(3);

        FileVersion fv = new FileVersion();
        fv.setWorkspaceId("ws-1");
        fv.setFilePath("/notes.txt");
        fv.setContentRef("/tmp/.versions/uuid-3");

        FileVersion saved = repository.save(fv);

        assertEquals(3, saved.getVersionNo());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForObject(sql.capture(), eq(Integer.class), eq("ws-1"), eq("/notes.txt"));
        String lower = sql.getValue().toLowerCase();
        assertTrue(lower.contains("coalesce(max(version_no), 0) + 1"),
                "version_no must be computed as COALESCE(MAX(version_no), 0) + 1; got: " + sql.getValue());
        assertTrue(lower.contains("where workspace_id = ?"),
                "query must be scoped to workspace_id; got: " + sql.getValue());
        assertTrue(lower.contains("file_path = ?"),
                "query must be scoped to file_path; got: " + sql.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void findByFilePath_queriesWithCorrectOrder() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq("ws-1"), eq("/doc.md")))
                .thenReturn(List.of());

        repository.findByFilePath("ws-1", "/doc.md");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), eq("ws-1"), eq("/doc.md"));
        String lower = sql.getValue().toLowerCase();
        assertTrue(lower.contains("order by version_no desc"),
                "history must be newest-first; got: " + sql.getValue());
        assertTrue(lower.contains("where workspace_id = ?"),
                "must filter by workspace_id; got: " + sql.getValue());
        assertTrue(lower.contains("file_path = ?"),
                "must filter by file_path; got: " + sql.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    void findById_returnsEmptyWhenNoMatch() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq("nonexistent")))
                .thenReturn(List.of());

        assertTrue(repository.findById("nonexistent").isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    void findById_returnsVersionWhenFound() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq("ver-1")))
                .thenAnswer(invocation -> {
                    // Simulate RowMapper converting a ResultSet row
                    FileVersion fv = new FileVersion();
                    fv.setId("ver-1");
                    fv.setWorkspaceId("ws-1");
                    fv.setFilePath("/notes.txt");
                    fv.setVersionNo(1);
                    fv.setContentRef("/tmp/.versions/uuid-1");
                    return List.of(fv);
                });

        var opt = repository.findById("ver-1");
        assertTrue(opt.isPresent());
        assertEquals("ver-1", opt.get().getId());
        assertEquals("ws-1", opt.get().getWorkspaceId());
    }

    @Test
    void deleteById_issuesDelete() {
        repository.deleteById("ver-1");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), eq("ver-1"));
        assertTrue(sql.getValue().toLowerCase().contains("delete from file_version"),
                "expected DELETE from file_version; got: " + sql.getValue());
    }
}
