package com.tepeu.service;

import com.tepeu.model.Workspace;
import com.tepeu.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 工作区生命周期：创建时建盘、删除时清库并尽量清盘。
 * 关联：WorkspaceRepository、WorkspacePathResolver、WorkspaceController。
 */
@Service
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);

    private final WorkspaceRepository repository;
    private final FileWatcherService fileWatcherService;

    public WorkspaceService(WorkspaceRepository repository, FileWatcherService fileWatcherService) {
        this.repository = repository;
        this.fileWatcherService = fileWatcherService;
    }

    public List<Workspace> listWorkspaces() {
        return repository.findAll();
    }

    public Optional<Workspace> getWorkspace(String id) {
        return repository.findById(id);
    }

    public Workspace createWorkspace(String name, String description, String type, String rootPath) {
        String id = UUID.randomUUID().toString();
        String normalizedType = normalizeType(type);
        if (rootPath == null || rootPath.isBlank()) {
            rootPath = "workspaces/" + id;
        }
        // 先建磁盘目录，再入库——避免「目录失败但 DB 行已存在」的孤儿行
        Path dir = Paths.get(System.getProperty("user.dir"), rootPath).normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建工作区目录: " + rootPath, e);
        }
        Workspace workspace = new Workspace(id, name, description, normalizedType, "local", rootPath);
        Workspace saved;
        try {
            saved = repository.save(workspace);
        } catch (RuntimeException e) {
            // 目录已建但入库失败：清掉目录，避免孤儿目录
            deleteWorkspaceDirectorySafely(rootPath);
            throw e;
        }
        // 新工作区目录加入文件监听（Phase 12）
        fileWatcherService.registerWorkspace(id, rootPath);
        return saved;
    }

    public Optional<Workspace> updateWorkspace(String id, String name, String description, String type) {
        Optional<Workspace> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        Workspace w = existing.get();
        if (name != null) w.setName(name);
        if (description != null) w.setDescription(description);
        if (type != null) w.setType(normalizeType(type));
        return Optional.of(repository.update(w));
    }

    /**
     * 删除工作区：先读 root_path，再删库（级联会话/记忆等），最后安全删除磁盘目录。
     * 仅删除位于 {@code user.dir} 下的路径，防止误删任意绝对路径。
     */
    /** 校验工作区类型：null/空白 → personal；非法值抛 400（而非 DB CHECK 500）。 */
    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            return "personal";
        }
        String t = type.trim();
        if (!"personal".equals(t) && !"enterprise".equals(t)) {
            throw new IllegalArgumentException("type 只能是 personal 或 enterprise");
        }
        return t;
    }

    public boolean deleteWorkspace(String id) {
        Optional<Workspace> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return false;
        }
        String rootPath = existing.get().getRootPath();
        if (rootPath == null || rootPath.isBlank()) {
            rootPath = "workspaces/" + id;
        }
        repository.deleteById(id);
        // 先摘监听再清盘，避免删除过程产生 file_changed 事件（Phase 12）
        fileWatcherService.unregisterWorkspace(id);
        deleteWorkspaceDirectorySafely(rootPath);
        return true;
    }

    /** 仅当目录落在 user.dir 内时递归删除；失败只记日志，不回滚 DB。 */
    void deleteWorkspaceDirectorySafely(String rootPath) {
        Path userDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path target = userDir.resolve(rootPath).normalize();
        if (!target.startsWith(userDir) || target.equals(userDir)) {
            log.warn("Skip disk cleanup: path outside user.dir or is user.dir itself: {}", target);
            return;
        }
        if (!Files.exists(target)) {
            return;
        }
        try {
            try (Stream<Path> walk = Files.walk(target)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            log.info("Deleted workspace directory: {}", target);
        } catch (Exception e) {
            log.warn("Failed to delete workspace directory {}: {}", target, e.toString());
        }
    }
}
