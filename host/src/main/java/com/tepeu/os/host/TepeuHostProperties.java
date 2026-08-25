package com.tepeu.os.host;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "tepeu")
public class TepeuHostProperties {

    private Path dataDir = Path.of(System.getProperty("user.home"), ".tepeu");
    private String model = "claude-sonnet-4-20250514";
    /** 空 = 随传输族推导（Anthropic / OpenAI / fake→anthropic）。 */
    private String family = "";
    private String principalId = "cli-user";
    private String workspaceId = "default";
    private boolean fakeLlm;
    private int promptBudget = 8000;

    public Path dataDir() {
        return dataDir;
    }

    public void setDataDir(Path dataDir) {
        this.dataDir = dataDir;
    }

    public String model() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String family() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    public String principalId() {
        return principalId;
    }

    public void setPrincipalId(String principalId) {
        this.principalId = principalId;
    }

    public String workspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public boolean fakeLlm() {
        return fakeLlm;
    }

    public void setFakeLlm(boolean fakeLlm) {
        this.fakeLlm = fakeLlm;
    }

    public int promptBudget() {
        return promptBudget;
    }

    public void setPromptBudget(int promptBudget) {
        this.promptBudget = promptBudget;
    }
}
