package com.tepeu.os.host;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.nio.file.Path;

/**
 * 宿主配置（{@code tepeu.*}）— model / family / base-url 可写 properties；
 * API key：环境变量（含 {@code ANTHROPIC_AUTH_TOKEN}）→ {@code tepeu.api-key} → 本机 CC Switch。
 * 勿把真实密钥提交进仓库。
 */
@ConfigurationProperties(prefix = "tepeu")
public class TepeuHostProperties {

    /** 数据目录：workspace/；未配 JDBC URL 时默认 sqlite 文件也落这里。 */
    private Path dataDir = Path.of(System.getProperty("user.home"), ".tepeu");
    /** JDBC URL，交给 Spring {@code DataSourceBuilder}。空则约定 sqlite 文件。 */
    @NestedConfigurationProperty
    private final Datasource datasource = new Datasource();
    /** 模型名，写入 LoopConfig。 */
    private String model = "claude-sonnet-4-20250514";
    /** 协议族；空 = 随 env key / 传输推导（anthropic | openai）。 */
    private String family = "";
    /** HTTP 基址；优先于 OPENAI_BASE_URL / ANTHROPIC_BASE_URL。 */
    private String baseUrl = "";
    /**
     * 可选密钥；优先用 {@code OPENAI_API_KEY} / {@code ANTHROPIC_API_KEY}。
     * 本地调试（如 Ollama 填占位）可用；勿提交真实密钥。
     */
    private String apiKey = "";
    private String principalId = "cli-user";
    private String workspaceId = "default";
    /** true 强制 FakeLlmTransport，不读密钥。 */
    private boolean fakeLlm;
    /** PromptAssembly 字符预算。 */
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

    public String baseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String apiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
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

    public Datasource getDatasource() {
        return datasource;
    }

    public String kernelJdbcUrl() {
        return firstUrl(datasource.getUrl(), "kernel.sqlite");
    }

    public String approvalsJdbcUrl() {
        return firstUrl(datasource.getApprovalsUrl(), "approvals.sqlite");
    }

    public String datasourceUsername() {
        return blankToEmpty(datasource.getUsername());
    }

    public String datasourcePassword() {
        return datasource.getPassword() == null ? "" : datasource.getPassword();
    }

    private String firstUrl(String configured, String defaultFile) {
        if (configured != null && !configured.isBlank()) {
            return configured.strip();
        }
        Path file = dataDir.resolve(defaultFile).toAbsolutePath().normalize();
        return "jdbc:sqlite:" + file.toString().replace('\\', '/');
    }

    private static String blankToEmpty(String s) {
        return s == null || s.isBlank() ? "" : s.strip();
    }

    public static class Datasource {
        private String url = "";
        private String approvalsUrl = "";
        private String username = "";
        private String password = "";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getApprovalsUrl() {
            return approvalsUrl;
        }

        public void setApprovalsUrl(String approvalsUrl) {
            this.approvalsUrl = approvalsUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
