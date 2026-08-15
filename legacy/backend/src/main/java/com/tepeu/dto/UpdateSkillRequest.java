package com.tepeu.dto;

/**
 * 启用/停用技能。
 */
public class UpdateSkillRequest {
    private Boolean enabled;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
}
