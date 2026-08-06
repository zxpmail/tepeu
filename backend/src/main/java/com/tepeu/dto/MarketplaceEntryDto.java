package com.tepeu.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 市场目录条目（浏览用）。
 * 关联：SkillMarketplaceService、MarketplaceView。
 */
public class MarketplaceEntryDto {

    private String id;
    private String slug;
    private String name;
    private String description;
    private String version;
    private String category;
    private List<String> tags = new ArrayList<>();
    /** builtin / local / github / remote / unavailable */
    private String availability;
    private boolean installed;
    private String installedSkillId;
    private String installedVersion;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags != null ? tags : new ArrayList<>(); }
    public String getAvailability() { return availability; }
    public void setAvailability(String availability) { this.availability = availability; }
    public boolean isInstalled() { return installed; }
    public void setInstalled(boolean installed) { this.installed = installed; }
    public String getInstalledSkillId() { return installedSkillId; }
    public void setInstalledSkillId(String installedSkillId) { this.installedSkillId = installedSkillId; }
    public String getInstalledVersion() { return installedVersion; }
    public void setInstalledVersion(String installedVersion) { this.installedVersion = installedVersion; }
}
