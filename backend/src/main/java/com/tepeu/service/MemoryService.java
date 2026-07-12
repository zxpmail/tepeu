package com.tepeu.service;

import com.tepeu.model.Memory;
import com.tepeu.repository.MemoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MemoryService {

    private final MemoryRepository repository;

    public MemoryService(MemoryRepository repository) {
        this.repository = repository;
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
        return repository.save(memory);
    }

    public Optional<Memory> updateMemory(String id, String content, List<String> tags) {
        Optional<Memory> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        Memory m = existing.get();
        if (content != null) m.setContent(content);
        if (tags != null) m.setTags(tags);
        return Optional.of(repository.update(m));
    }

    public boolean deleteMemory(String id) {
        if (repository.findById(id).isEmpty()) {
            return false;
        }
        repository.deleteById(id);
        return true;
    }
}
