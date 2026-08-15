package com.tepeu.model;

import java.time.LocalDateTime;

public class Workspace {
    private String id;
    private String name;
    private String description;
    private String type;       // "personal" | "enterprise"
    private String ownerId;
    private String rootPath;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Workspace() {}

    public Workspace(String id, String name, String description, String type, String ownerId) {
        this(id, name, description, type, ownerId, null);
    }

    public Workspace(String id, String name, String description, String type, String ownerId, String rootPath) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.ownerId = ownerId;
        this.rootPath = rootPath;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getRootPath() { return rootPath; }
    public void setRootPath(String rootPath) { this.rootPath = rootPath; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
