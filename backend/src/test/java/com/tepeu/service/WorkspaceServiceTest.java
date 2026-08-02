package com.tepeu.service;

import com.tepeu.model.Workspace;
import com.tepeu.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 工作区删除：清库后安全清盘（仅 user.dir 内路径）。
 */
class WorkspaceServiceTest {

    private WorkspaceRepository repository;
    private WorkspaceService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        repository = mock(WorkspaceRepository.class);
        service = new WorkspaceService(repository);
        System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
    }

    @Test
    void deleteWorkspace_removesDbRowAndDiskDirectory() throws Exception {
        String id = "ws-del-1";
        String root = "workspaces/" + id;
        Path dir = tempDir.resolve(root);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("note.txt"), "hello");

        Workspace w = new Workspace(id, "T", null, "personal", "local", root);
        when(repository.findById(id)).thenReturn(Optional.of(w));

        assertTrue(service.deleteWorkspace(id));
        verify(repository).deleteById(id);
        assertFalse(Files.exists(dir));
    }

    @Test
    void deleteWorkspace_skipsDiskWhenOutsideUserDir() {
        String id = "ws-abs";
        // 绝对路径解析后若仍落在 tempDir 外则跳过；这里用明显越界路径
        Workspace w = new Workspace(id, "T", null, "personal", "local", "../outside-tepeu-ws");
        when(repository.findById(id)).thenReturn(Optional.of(w));

        assertTrue(service.deleteWorkspace(id));
        verify(repository).deleteById(id);
        // 不应抛错；越界仅跳过清盘
    }

    @Test
    void deleteWorkspace_returnsFalseWhenMissing() {
        when(repository.findById("nope")).thenReturn(Optional.empty());
        assertFalse(service.deleteWorkspace("nope"));
        verify(repository, never()).deleteById(any());
    }
}
