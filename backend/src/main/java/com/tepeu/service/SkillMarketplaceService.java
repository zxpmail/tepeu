package com.tepeu.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tepeu.dto.MarketplaceCatalogResponse;
import com.tepeu.dto.MarketplaceEntryDto;
import com.tepeu.model.Skill;
import com.tepeu.repository.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 应用市场 — 内置索引 + 本机 ReqForge 扫描 + 可选远程清单；一键安装到工作区。
 * 关联：MarketplaceController、SkillService、catalog.json。
 */
@Service
public class SkillMarketplaceService {

    private static final Logger log = LoggerFactory.getLogger(SkillMarketplaceService.class);
    private static final Pattern FM_NAME = Pattern.compile("(?m)^name:\\s*[\"']?([^\"'\\r\\n]+)[\"']?\\s*$");
    private static final Pattern FM_DESC = Pattern.compile("(?m)^description:\\s*[\"']?(.+?)[\"']?\\s*$");
    private static final Pattern FM_VERSION = Pattern.compile("(?m)^version:\\s*[\"']?([^\"'\\r\\n]+)[\"']?\\s*$");
    private static final Pattern FRONTMATTER = Pattern.compile(
            "^---\\r?\\n(.*?)\\r?\\n---\\r?\\n?", Pattern.DOTALL);

    private final SkillService skillService;
    private final SkillRepository skillRepository;
    private final ObjectMapper objectMapper;
    private final String manifestUrl;
    private final HttpClient httpClient;

    public SkillMarketplaceService(
            SkillService skillService,
            SkillRepository skillRepository,
            ObjectMapper objectMapper,
            @Value("${tepeu.marketplace.manifest-url:}") String manifestUrl) {
        this.skillService = skillService;
        this.skillRepository = skillRepository;
        this.objectMapper = objectMapper;
        this.manifestUrl = manifestUrl != null ? manifestUrl.trim() : "";
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** 浏览目录；q 过滤名称/描述/slug；workspaceId 用于标记已安装 */
    public MarketplaceCatalogResponse listCatalog(String workspaceId, String q) {
        Map<String, CatalogEntry> byId = new LinkedHashMap<>();
        String catalogVersion = "1.0";
        try {
            BuiltinCatalog builtin = loadBuiltinCatalog();
            catalogVersion = builtin.version != null ? builtin.version : catalogVersion;
            for (CatalogEntry e : builtin.entries) {
                byId.put(e.id, e);
            }
        } catch (IOException e) {
            log.warn("加载内置市场目录失败: {}", e.toString());
        }

        Path localRoot = skillService.resolveLocalReqForgeRootPublic();
        if (localRoot != null) {
            mergeLocalScan(byId, localRoot);
        }

        MarketplaceCatalogResponse resp = new MarketplaceCatalogResponse();
        resp.setCatalogVersion(catalogVersion);
        resp.setLocalRoot(localRoot != null ? localRoot.toString() : null);
        resp.setRemoteManifestUrl(manifestUrl.isBlank() ? null : manifestUrl);

        if (!manifestUrl.isBlank()) {
            try {
                List<CatalogEntry> remote = fetchRemoteManifest(manifestUrl);
                for (CatalogEntry e : remote) {
                    byId.put(e.id, e);
                }
                resp.setRemoteLoaded(true);
            } catch (Exception e) {
                resp.setRemoteLoaded(false);
                resp.setRemoteError(e.getMessage());
                log.warn("远程市场清单失败: {}", e.toString());
            }
        }

        Map<String, Skill> installedBySlug = new LinkedHashMap<>();
        if (workspaceId != null && !workspaceId.isBlank()) {
            for (Skill s : skillRepository.findByWorkspaceId(workspaceId)) {
                installedBySlug.put(s.getSlug(), s);
            }
        }

        String query = q != null ? q.trim().toLowerCase(Locale.ROOT) : "";
        List<MarketplaceEntryDto> out = new ArrayList<>();
        for (CatalogEntry e : byId.values()) {
            if (!query.isEmpty() && !matches(e, query)) {
                continue;
            }
            MarketplaceEntryDto dto = toDto(e, localRoot, installedBySlug);
            out.add(dto);
        }
        out.sort(Comparator
                .comparing((MarketplaceEntryDto d) -> !"builtin".equals(d.getCategory()))
                .thenComparing(MarketplaceEntryDto::getName, String.CASE_INSENSITIVE_ORDER));
        resp.setEntries(out);
        return resp;
    }

    /** 按条目 id 或 slug 安装到工作区 */
    public Skill install(String workspaceId, String entryId, String slug) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        if ((entryId == null || entryId.isBlank()) && (slug == null || slug.isBlank())) {
            throw new IllegalArgumentException("entryId or slug is required");
        }
        CatalogEntry entry = findRawEntry(entryId, slug);
        ResolvedContent resolved = resolveContent(entry);
        return skillService.installWithMeta(
                workspaceId, entry.name, resolved.markdown, resolved.source, entry.version);
    }

    /** 查找原始条目（含 classpath / relativePath / url） */
    private CatalogEntry findRawEntry(String entryId, String slug) {
        try {
            BuiltinCatalog builtin = loadBuiltinCatalog();
            for (CatalogEntry e : builtin.entries) {
                if (entryId != null && entryId.equals(e.id)) {
                    return e;
                }
                if (slug != null && slug.equalsIgnoreCase(e.slug)) {
                    return e;
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("内置目录不可用: " + e.getMessage());
        }
        Path localRoot = skillService.resolveLocalReqForgeRootPublic();
        if (localRoot != null && slug != null) {
            Path skillMd = localRoot.resolve("core/skills").resolve(slug).resolve("SKILL.md");
            if (Files.isRegularFile(skillMd)) {
                CatalogEntry e = new CatalogEntry();
                e.id = "local-" + slug;
                e.slug = slug;
                e.name = slug;
                e.relativePath = "core/skills/" + slug + "/SKILL.md";
                e.category = "local";
                enrichFromFile(e, skillMd);
                return e;
            }
        }
        if (!manifestUrl.isBlank()) {
            try {
                for (CatalogEntry e : fetchRemoteManifest(manifestUrl)) {
                    if (entryId != null && entryId.equals(e.id)) {
                        return e;
                    }
                    if (slug != null && slug.equalsIgnoreCase(e.slug)) {
                        return e;
                    }
                }
            } catch (Exception ignored) {
                // 下面统一抛
            }
        }
        throw new IllegalArgumentException(
                entryId != null ? "未知市场条目: " + entryId : "未知技能: " + slug);
    }

    private ResolvedContent resolveContent(CatalogEntry entry) {
        if (entry.classpath != null && !entry.classpath.isBlank()) {
            try {
                ClassPathResource res = new ClassPathResource(entry.classpath);
                if (!res.exists()) {
                    throw new IllegalArgumentException("内置技能文件不存在: " + entry.classpath);
                }
                try (InputStream in = res.getInputStream()) {
                    String md = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    return new ResolvedContent(md, "builtin:" + entry.classpath);
                }
            } catch (IOException e) {
                throw new IllegalArgumentException("读取内置技能失败: " + e.getMessage());
            }
        }
        Path localRoot = skillService.resolveLocalReqForgeRootPublic();
        if (entry.relativePath != null && !entry.relativePath.isBlank()) {
            if (localRoot != null) {
                Path file = localRoot.resolve(entry.relativePath).normalize();
                if (file.startsWith(localRoot.normalize()) && Files.isRegularFile(file)) {
                    try {
                        String md = Files.readString(file, StandardCharsets.UTF_8);
                        return new ResolvedContent(md, "local:" + file);
                    } catch (IOException e) {
                        throw new IllegalArgumentException("读取本机技能失败: " + e.getMessage());
                    }
                }
            }
            String url = SkillService.REQFORGE_RAW_BASE + entry.relativePath;
            try {
                String md = downloadText(url);
                return new ResolvedContent(md, "github:" + url);
            } catch (RuntimeException githubEx) {
                throw new IllegalArgumentException(
                        "无法获取技能内容（本机无文件且远程失败）: " + githubEx.getMessage());
            }
        }
        if (entry.url != null && !entry.url.isBlank()) {
            String md = downloadText(entry.url);
            return new ResolvedContent(md, "remote:" + entry.url);
        }
        throw new IllegalArgumentException("条目无可安装内容: " + entry.id);
    }

    private String downloadText(String url) {
        URI uri = SkillService.validateRemoteUrl(url);
        try {
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Tepeu-Marketplace/0.1")
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalArgumentException("download failed: HTTP " + resp.statusCode());
            }
            byte[] body = resp.body();
            if (body.length > SkillService.MAX_SKILL_CONTENT_BYTES) {
                throw new IllegalArgumentException("skill content too large");
            }
            return new String(body, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("download failed: " + e.getMessage());
        }
    }

    private BuiltinCatalog loadBuiltinCatalog() throws IOException {
        ClassPathResource res = new ClassPathResource("marketplace/catalog.json");
        try (InputStream in = res.getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            BuiltinCatalog cat = new BuiltinCatalog();
            cat.version = text(root, "version");
            cat.entries = new ArrayList<>();
            JsonNode arr = root.get("entries");
            if (arr != null && arr.isArray()) {
                for (JsonNode n : arr) {
                    CatalogEntry e = parseEntryNode(n, "builtin");
                    if (e.id != null) {
                        cat.entries.add(e);
                    }
                }
            }
            return cat;
        }
    }

    private List<CatalogEntry> fetchRemoteManifest(String url) throws Exception {
        String json = downloadText(url);
        JsonNode root = objectMapper.readTree(json);
        JsonNode arr = root.isArray() ? root : root.get("entries");
        List<CatalogEntry> list = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode n : arr) {
                CatalogEntry e = parseEntryNode(n, "remote");
                if (e.id == null && e.slug != null) {
                    e.id = "remote-" + e.slug;
                }
                if (e.id != null) {
                    list.add(e);
                }
            }
        }
        return list;
    }

    private CatalogEntry parseEntryNode(JsonNode n, String defaultCategory) {
        CatalogEntry e = new CatalogEntry();
        e.id = text(n, "id");
        e.slug = text(n, "slug");
        e.name = text(n, "name");
        e.description = text(n, "description");
        e.version = text(n, "version");
        e.category = text(n, "category");
        if (e.category == null || e.category.isBlank()) {
            e.category = defaultCategory;
        }
        e.classpath = text(n, "classpath");
        e.relativePath = text(n, "relativePath");
        e.url = text(n, "url");
        e.tags = new ArrayList<>();
        JsonNode tags = n.get("tags");
        if (tags != null && tags.isArray()) {
            for (JsonNode t : tags) {
                if (t.isTextual()) {
                    e.tags.add(t.asText());
                }
            }
        }
        if (e.slug == null && e.name != null) {
            e.slug = SkillService.slugify(e.name);
        }
        if (e.name == null) {
            e.name = e.slug;
        }
        return e;
    }

    private void mergeLocalScan(Map<String, CatalogEntry> byId, Path localRoot) {
        Path skillsDir = localRoot.resolve("core/skills");
        if (!Files.isDirectory(skillsDir)) {
            return;
        }
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(skillsDir)) {
            for (Path dir : dirs) {
                if (!Files.isDirectory(dir) || dir.getFileName().toString().startsWith("_")) {
                    continue;
                }
                Path skillMd = dir.resolve("SKILL.md");
                if (!Files.isRegularFile(skillMd)) {
                    continue;
                }
                String slug = dir.getFileName().toString();
                String id = "reqforge-" + slug;
                CatalogEntry existing = byId.values().stream()
                        .filter(x -> slug.equals(x.slug) || id.equals(x.id))
                        .findFirst()
                        .orElse(null);
                if (existing != null) {
                    existing.relativePath = "core/skills/" + slug + "/SKILL.md";
                    enrichFromFile(existing, skillMd);
                    continue;
                }
                CatalogEntry e = new CatalogEntry();
                e.id = "local-" + slug;
                e.slug = slug;
                e.name = slug;
                e.category = "local";
                e.relativePath = "core/skills/" + slug + "/SKILL.md";
                e.tags = List.of("local", "reqforge");
                enrichFromFile(e, skillMd);
                byId.put(e.id, e);
            }
        } catch (IOException e) {
            log.warn("扫描本机 ReqForge skills 失败: {}", e.toString());
        }
    }

    private void enrichFromFile(CatalogEntry e, Path skillMd) {
        try {
            String raw = Files.readString(skillMd, StandardCharsets.UTF_8);
            Matcher fm = FRONTMATTER.matcher(raw.replaceAll("(?s)<!--.*?-->", "").trim());
            if (fm.find()) {
                String block = fm.group(1);
                Matcher nm = FM_NAME.matcher(block);
                if (nm.find()) {
                    e.name = nm.group(1).trim();
                    if (e.slug == null || e.slug.isBlank()) {
                        e.slug = SkillService.slugify(e.name);
                    }
                }
                Matcher dm = FM_DESC.matcher(block);
                if (dm.find()) {
                    e.description = dm.group(1).trim();
                }
                Matcher vm = FM_VERSION.matcher(block);
                if (vm.find()) {
                    e.version = vm.group(1).trim();
                }
            }
        } catch (IOException ignored) {
            // 保留已有元数据
        }
    }

    private MarketplaceEntryDto toDto(
            CatalogEntry e, Path localRoot, Map<String, Skill> installedBySlug) {
        MarketplaceEntryDto dto = new MarketplaceEntryDto();
        dto.setId(e.id);
        dto.setSlug(e.slug);
        dto.setName(e.name != null ? e.name : e.slug);
        dto.setDescription(e.description);
        dto.setVersion(e.version);
        dto.setCategory(e.category);
        dto.setTags(e.tags != null ? e.tags : List.of());
        dto.setAvailability(computeAvailability(e, localRoot));
        Skill installed = e.slug != null ? installedBySlug.get(e.slug) : null;
        if (installed == null && e.name != null) {
            installed = installedBySlug.get(SkillService.slugify(e.name));
        }
        if (installed != null) {
            dto.setInstalled(true);
            dto.setInstalledSkillId(installed.getId());
            dto.setInstalledVersion(installed.getInstallVersion());
        }
        return dto;
    }

    private String computeAvailability(CatalogEntry e, Path localRoot) {
        if (e.classpath != null && !e.classpath.isBlank()) {
            return "builtin";
        }
        if (e.url != null && !e.url.isBlank()) {
            return "remote";
        }
        if (e.relativePath != null && !e.relativePath.isBlank()) {
            if (localRoot != null) {
                Path file = localRoot.resolve(e.relativePath);
                if (Files.isRegularFile(file)) {
                    return "local";
                }
            }
            return "github";
        }
        return "unavailable";
    }

    private static boolean matches(CatalogEntry e, String q) {
        return contains(e.name, q) || contains(e.slug, q) || contains(e.description, q)
                || contains(e.category, q)
                || (e.tags != null && e.tags.stream().anyMatch(t -> contains(t, q)));
    }

    private static boolean contains(String s, String q) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(q);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v != null && v.isTextual() ? v.asText() : null;
    }

    private static final class BuiltinCatalog {
        String version;
        List<CatalogEntry> entries;
    }

    private static final class CatalogEntry {
        String id;
        String slug;
        String name;
        String description;
        String version;
        String category;
        List<String> tags;
        String classpath;
        String relativePath;
        String url;
    }

    private record ResolvedContent(String markdown, String source) {}
}
