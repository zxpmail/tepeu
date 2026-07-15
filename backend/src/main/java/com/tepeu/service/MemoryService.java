package com.tepeu.service;

import com.tepeu.model.Memory;
import com.tepeu.repository.MemoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MemoryService {

    private final MemoryRepository repository;
    private final MemoryFileMirror fileMirror;

    public MemoryService(MemoryRepository repository, MemoryFileMirror fileMirror) {
        this.repository = repository;
        this.fileMirror = fileMirror;
    }

    public List<Memory> listMemories(String workspaceId, int limit, String cursor) {
        return repository.findByWorkspaceId(workspaceId, limit, cursor);
    }

    public List<Memory> searchMemories(String workspaceId, String query, List<String> tags, int limit, String cursor) {
        return repository.search(workspaceId, query, tags, limit, cursor);
    }

    public Optional<Memory> getMemory(String id) {
        return repository.findById(id);
    }

    public Memory createMemory(String workspaceId, String source, String content, List<String> tags) {
        Memory memory = new Memory();
        memory.setWorkspaceId(workspaceId);
        memory.setSource(source);
        memory.setContent(content);
        memory.setTags(tags);
        Memory saved = repository.save(memory);
        // DB 权威；MD 仅供 Agent 阅读（双写）
        fileMirror.write(saved);
        return saved;
    }

    public Optional<Memory> updateMemory(String id, String content, List<String> tags) {
        Optional<Memory> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        Memory m = existing.get();
        if (content != null) m.setContent(content);
        if (tags != null) m.setTags(tags);
        Memory updated = repository.update(m);
        fileMirror.write(updated);
        return Optional.of(updated);
    }

    public boolean deleteMemory(String id) {
        Optional<Memory> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return false;
        }
        Memory m = existing.get();
        repository.deleteById(id);
        fileMirror.delete(m.getWorkspaceId(), id);
        return true;
    }
}
