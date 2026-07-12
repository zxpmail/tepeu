package com.tepeu.service;

import com.tepeu.model.Workspace;
import com.tepeu.repository.WorkspaceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class WorkspaceService {

    private final WorkspaceRepository repository;

    public WorkspaceService(WorkspaceRepository repository) {
        this.repository = repository;
    }

    public List<Workspace> listWorkspaces() {
        return repository.findAll();
    }

    public Optional<Workspace> getWorkspace(String id) {
        return repository.findById(id);
    }

    public Workspace createWorkspace(String name, String description, String type, String rootPath) {
        String id = UUID.randomUUID().toString();
        if (rootPath == null || rootPath.isBlank()) {
            rootPath = "workspaces/" + id;
        }
        Workspace workspace = new Workspace(id, name, description, type, "local", rootPath);
        Workspace saved = repository.save(workspace);
        // 立刻创建磁盘目录，避免「工作区有了但文件树是空的且写不进去」
        try {
            java.nio.file.Path dir = java.nio.file.Paths.get(
                    System.getProperty("user.dir"), rootPath).normalize();
            java.nio.file.Files.createDirectories(dir);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to create workspace directory: " + rootPath, e);
        }
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
        if (type != null) w.setType(type);
        return Optional.of(repository.update(w));
    }

    public boolean deleteWorkspace(String id) {
        if (repository.findById(id).isEmpty()) {
            return false;
        }
        repository.deleteById(id);
        return true;
    }
}
