package com.tepeu.agent.mcp;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 将 MCP 服务器工具桥接到 ChatService 工具链（再经 Hook / 可视化装饰器）。
 * 工具名格式：{@code mcp_<server>_<tool>}，避免多 server 同名冲突；结果短时缓存。
 * 同时汇总 Resources 供状态 API（只读列表，不注入模型工具面）。
 * 关联：ChatService、DangerousToolHook、McpController。
 */
@Component
public class McpToolBridge {

    private static final Logger log = LoggerFactory.getLogger(McpToolBridge.class);
    public static final String NAME_PREFIX = "mcp_";

    /** 状态里的一条 MCP 资源。 */
    public record ResourceRef(
            String server,
            String uri,
            String name,
            String description,
            String mimeType) {}

    /** 运行时快照：工具 + 资源 + 诊断。 */
    public record StatusSnapshot(
            boolean enabled,
            int clientCount,
            List<String> tools,
            List<ResourceRef> resources,
            String warning,
            String note) {}

    private record CacheEntry(
            ToolCallback[] tools,
            List<ResourceRef> resources,
            int clientCount,
            String warning,
            Instant loadedAt) {}

    private final ObjectProvider<List<McpSyncClient>> syncClients;
    private final boolean clientEnabled;
    private final Duration cacheTtl;
    private final AtomicReference<CacheEntry> cache = new AtomicReference<>();

    @Autowired
    public McpToolBridge(
            ObjectProvider<List<McpSyncClient>> syncClients,
            @Value("${spring.ai.mcp.client.enabled:false}") boolean clientEnabled,
            @Value("${tepeu.mcp.cache-ttl-seconds:30}") long cacheTtlSeconds) {
        this.syncClients = syncClients;
        this.clientEnabled = clientEnabled;
        this.cacheTtl = Duration.ofSeconds(Math.max(0, cacheTtlSeconds));
    }

    /** MCP 客户端是否启用（配置项，非运行时探测）。 */
    public boolean isClientEnabled() {
        return clientEnabled;
    }

    /** 当前可用 MCP 工具（已加 mcp_&lt;server&gt;_ 前缀）；无连接时为空。 */
    public ToolCallback[] callbacks() {
        return load().tools();
    }

    /** 工具名列表。 */
    public List<String> toolNames() {
        return toolNamesFrom(load().tools());
    }

    /** 资源只读列表（供状态/UI）。 */
    public List<ResourceRef> resources() {
        return load().resources();
    }

    /** 仪表盘/状态 API 用快照。 */
    public StatusSnapshot status() {
        CacheEntry entry = load();
        String note;
        if (!clientEnabled) {
            note = "未启用：在 application.yml 设置 spring.ai.mcp.client.enabled=true 并配置 stdio/SSE（见 mcp-servers.example.yml）";
        } else if (entry.clientCount() == 0) {
            note = "已启用但无可用连接：请配置 spring.ai.mcp.client.stdio/sse.connections";
        } else {
            note = "MCP 工具已并入对话工具链并走 Hook（mcp_* 需审批；自主调度会话也不免批）";
        }
        return new StatusSnapshot(
                clientEnabled,
                entry.clientCount(),
                toolNamesFrom(entry.tools()),
                entry.resources(),
                entry.warning(),
                note);
    }

    /** 按 URI 读取资源（遍历已连接 client）。 */
    public String readResource(String uri) {
        if (!clientEnabled) {
            throw new IllegalStateException("MCP 客户端未启用");
        }
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("uri 不能为空");
        }
        List<McpSyncClient> clients = syncClients.getIfAvailable(List::of);
        if (clients == null || clients.isEmpty()) {
            throw new IllegalStateException("没有已连接的 MCP server");
        }
        RuntimeException last = null;
        for (McpSyncClient client : clients) {
            try {
                McpSchema.ReadResourceResult result = client.readResource(
                        new McpSchema.ReadResourceRequest(uri.trim()));
                if (result == null || result.contents() == null || result.contents().isEmpty()) {
                    continue;
                }
                StringBuilder sb = new StringBuilder();
                for (McpSchema.ResourceContents c : result.contents()) {
                    if (c instanceof McpSchema.TextResourceContents text) {
                        if (!sb.isEmpty()) sb.append('\n');
                        sb.append(text.text() == null ? "" : text.text());
                    } else if (c instanceof McpSchema.BlobResourceContents blob) {
                        if (!sb.isEmpty()) sb.append('\n');
                        sb.append("[binary base64 length=")
                                .append(blob.blob() == null ? 0 : blob.blob().length())
                                .append(']');
                    }
                }
                if (!sb.isEmpty()) {
                    return sb.toString();
                }
            } catch (RuntimeException e) {
                last = e;
            }
        }
        if (last != null) {
            throw new IllegalArgumentException("读取资源失败: " + last.getMessage(), last);
        }
        throw new IllegalArgumentException("未找到资源: " + uri);
    }

    /** 测试或配置变更后强制刷新。 */
    public void invalidateCache() {
        cache.set(null);
    }

    private CacheEntry load() {
        if (!clientEnabled) {
            return new CacheEntry(new ToolCallback[0], List.of(), 0, null, Instant.EPOCH);
        }
        CacheEntry existing = cache.get();
        if (existing != null && !cacheTtl.isZero()
                && Instant.now().isBefore(existing.loadedAt().plus(cacheTtl))) {
            return existing;
        }
        CacheEntry fresh = reload();
        cache.set(fresh);
        return fresh;
    }

    private CacheEntry reload() {
        List<McpSyncClient> clients = syncClients.getIfAvailable(List::of);
        if (clients == null || clients.isEmpty()) {
            String warn = "MCP 已启用但未发现任何 sync client（请检查 connections 配置）";
            log.warn(warn);
            return new CacheEntry(new ToolCallback[0], List.of(), 0, warn, Instant.now());
        }

        List<ToolCallback> prefixed = new ArrayList<>();
        List<ResourceRef> resources = new ArrayList<>();
        Map<String, Integer> serverCounters = new LinkedHashMap<>();
        String warning = null;

        for (int i = 0; i < clients.size(); i++) {
            McpSyncClient client = clients.get(i);
            String serverKey = uniqueServerKey(client, i, serverCounters);
            try {
                List<ToolCallback> raw = SyncMcpToolCallbackProvider.syncToolCallbacks(List.of(client));
                for (ToolCallback cb : raw) {
                    String base = stripMcpPrefix(cb.getToolDefinition().name());
                    String name = NAME_PREFIX + serverKey + "_" + sanitizeToken(base);
                    prefixed.add(new RenamingToolCallback(cb, name));
                }
            } catch (RuntimeException e) {
                log.warn("Failed to load MCP tools from server={}: {}", serverKey, e.toString());
                warning = "部分 server 工具加载失败: " + e.getMessage();
            }
            try {
                McpSchema.ListResourcesResult listed = client.listResources();
                if (listed != null && listed.resources() != null) {
                    for (McpSchema.Resource r : listed.resources()) {
                        resources.add(new ResourceRef(
                                serverKey,
                                r.uri(),
                                r.name(),
                                r.description(),
                                r.mimeType()));
                    }
                }
            } catch (RuntimeException e) {
                log.debug("listResources skipped for server={}: {}", serverKey, e.toString());
            }
        }

        log.debug("MCP bridge loaded {} tool(s), {} resource(s) from {} client(s)",
                prefixed.size(), resources.size(), clients.size());
        return new CacheEntry(
                prefixed.toArray(ToolCallback[]::new),
                List.copyOf(resources),
                clients.size(),
                warning,
                Instant.now());
    }

    /** 生成稳定且唯一的 server 段（用于工具名前缀）。 */
    static String uniqueServerKey(McpSyncClient client, int index, Map<String, Integer> counters) {
        String base = "s" + index;
        try {
            McpSchema.Implementation info = client.getServerInfo();
            if (info != null && info.name() != null && !info.name().isBlank()) {
                base = sanitizeToken(info.name());
            }
        } catch (RuntimeException ignored) {
            // 未初始化时用索引
        }
        if (base.isBlank()) {
            base = "s" + index;
        }
        int n = counters.merge(base, 1, Integer::sum);
        return n == 1 ? base : base + n;
    }

    static String stripMcpPrefix(String name) {
        if (name == null) {
            return "tool";
        }
        String n = name;
        while (n.regionMatches(true, 0, NAME_PREFIX, 0, NAME_PREFIX.length())) {
            n = n.substring(NAME_PREFIX.length());
        }
        return n.isBlank() ? "tool" : n;
    }

    static String sanitizeToken(String raw) {
        if (raw == null || raw.isBlank()) {
            return "x";
        }
        String s = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        s = s.replaceAll("^_+|_+$", "");
        return s.isBlank() ? "x" : s;
    }

    private static List<String> toolNamesFrom(ToolCallback[] tools) {
        List<String> names = new ArrayList<>(tools.length);
        for (ToolCallback cb : tools) {
            names.add(cb.getToolDefinition().name());
        }
        return names;
    }
}
