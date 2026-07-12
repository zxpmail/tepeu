package com.tepeu.service;

import com.tepeu.model.Skill;
import com.tepeu.repository.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 工作区技能 — 安装/启用/内置种入；支持粘贴、URL、ZIP。
 * 关联：SkillRepository、SkillController、AgentOrchestrator。
 */
@Service
public class SkillService {

    private static final Logger log = LoggerFactory.getLogger(SkillService.class);

    /** 本轮调用技能注入对话的合计正文上限 */
    public static final int MAX_ENABLED_CHARS = 48 * 1024;
    /** 从用户消息解析 /skill 或 @skill */
    private static final Pattern SKILL_MENTION =
            Pattern.compile("(?:^|[\\s])([/@])([\\w\\u4e00-\\u9fff][\\w\\-\\u4e00-\\u9fff]*)");
    public static final String BUILTIN_SLUG = "coding-assistant";
    /** ReqForge 官方仓库（raw 安装源） */
    public static final String REQFORGE_RAW_BASE =
            "https://raw.githubusercontent.com/zxpmail/ReqForge/main/";
    /** 远程下载 / ZIP 体积上限 */
    public static final int MAX_PACKAGE_BYTES = 2 * 1024 * 1024;
    /** 单技能正文上限 */
    public static final int MAX_SKILL_CONTENT_BYTES = 256 * 1024;

    private static final Pattern FRONTMATTER = Pattern.compile(
            "^---\\r?\\n(.*?)\\r?\\n---\\r?\\n?(.*)$", Pattern.DOTALL);
    private static final Pattern FM_NAME = Pattern.compile("(?m)^name:\\s*[\"']?([^\"'\\r\\n]+)[\"']?\\s*$");
    private static final Pattern FM_DESC = Pattern.compile("(?m)^description:\\s*[\"']?(.+?)[\"']?\\s*$");
    private static final Pattern HTML_COMMENT = Pattern.compile("(?s)<!--.*?-->");

    private final SkillRepository repository;
    private final HttpClient httpClient;
    private final String configuredLocalPath;

    public SkillService(
            SkillRepository repository,
            @Value("${tepeu.reqforge.local-path:}") String configuredLocalPath) {
        this.repository = repository;
        this.configuredLocalPath = configuredLocalPath;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** 列表；若无内置则种入编程助手（默认未启用） */
    public List<Skill> listSkills(String workspaceId) {
        ensureBuiltin(workspaceId);
        return repository.findByWorkspaceId(workspaceId);
    }

    public void ensureBuiltin(String workspaceId) {
        if (repository.findByWorkspaceAndSlug(workspaceId, BUILTIN_SLUG).isPresent()) {
            return;
        }
        Skill s = new Skill();
        s.setWorkspaceId(workspaceId);
        s.setSlug(BUILTIN_SLUG);
        s.setName("编程助手");
        s.setDescription("用 Tepeu 文件与命令工具写代码、运行并修错");
        s.setContent(BUILTIN_CONTENT);
        s.setEnabled(false);
        s.setBuiltin(true);
        repository.save(s);
    }

    public Skill install(String workspaceId, String nameHint, String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
        return saveParsed(workspaceId, nameHint, content.trim());
    }

    /** 从 http(s) URL 安装：.zip 按包解析，否则当 Markdown */
    public Skill installFromUrl(String workspaceId, String nameHint, String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url is required");
        }
        URI uri = validateRemoteUrl(url.trim());
        byte[] bytes = download(uri);
        String path = uri.getPath() != null ? uri.getPath().toLowerCase(Locale.ROOT) : "";
        if (path.endsWith(".zip") || looksLikeZip(bytes)) {
            return installFromZip(workspaceId, nameHint, bytes);
        }
        String text = new String(bytes, StandardCharsets.UTF_8);
        return saveParsed(workspaceId, nameHint, text);
    }

    /** 从 ZIP 字节安装：找 SKILL.md，并附带 references 下 md（有预算） */
    public Skill installFromZip(String workspaceId, String nameHint, byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IllegalArgumentException("zip is empty");
        }
        if (zipBytes.length > MAX_PACKAGE_BYTES) {
            throw new IllegalArgumentException("zip too large (max " + MAX_PACKAGE_BYTES + " bytes)");
        }
        String markdown = extractSkillMarkdownFromZip(zipBytes);
        return saveParsed(workspaceId, nameHint, markdown, false);
    }

    /**
     * 一键安装 ReqForge 编程相关 skill + agent。
     * 优先 GitHub raw；失败则回退到本机目录（tepeu.reqforge.local-path）。
     */
    public PackResult installReqForgeCodingPack(String workspaceId) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId is required");
        }
        ensureBuiltin(workspaceId);
        repository.findByWorkspaceAndSlug(workspaceId, BUILTIN_SLUG).ifPresent(s -> {
            if (!s.isEnabled()) {
                repository.updateEnabled(s.getId(), true);
            }
        });

        List<PackItem> items = List.of(
                new PackItem("core/skills/dev-builder/SKILL.md", "dev-builder", true),
                new PackItem("core/skills/bug-fixer/SKILL.md", "bug-fixer", true),
                new PackItem("core/skills/code-review/SKILL.md", "code-review", false),
                new PackItem("core/agents/implementer.md", "agent-implementer", false),
                new PackItem("core/agents/planner.md", "agent-planner", false),
                new PackItem("core/agents/test-writer.md", "agent-test-writer", false),
                new PackItem("core/agents/code-reviewer.md", "agent-code-reviewer", false)
        );

        Path localRoot = resolveLocalReqForgeRoot();
        int installed = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        List<Skill> skills = new ArrayList<>();
        String sourceHint = null;

        for (PackItem item : items) {
            try {
                String markdown = fetchPackMarkdown(item.path(), localRoot);
                if (sourceHint == null) {
                    sourceHint = localRoot != null && Files.isRegularFile(localRoot.resolve(item.path()))
                            ? "local:" + localRoot
                            : "github";
                }
                Skill s = saveParsed(workspaceId, null, markdown, false);
                if (item.enable()) {
                    repository.updateEnabled(s.getId(), true);
                    s = repository.findById(s.getId()).orElse(s);
                }
                skills.add(s);
                installed++;
            } catch (RuntimeException e) {
                failed++;
                errors.add(item.path() + ": " + e.getMessage());
                log.warn("ReqForge pack item failed {}: {}", item.path(), e.toString());
            }
        }
        if (sourceHint != null && installed > 0) {
            log.info("ReqForge coding pack installed from {}", sourceHint);
        }
        return new PackResult(installed, failed, errors, skills);
    }

    /** 先试 GitHub raw，失败再读本机 ReqForge */
    private String fetchPackMarkdown(String relativePath, Path localRoot) {
        String url = REQFORGE_RAW_BASE + relativePath;
        try {
            URI uri = validateRemoteUrl(url);
            byte[] bytes = download(uri);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (RuntimeException githubEx) {
            if (localRoot != null) {
                Path file = localRoot.resolve(relativePath).normalize();
                if (!file.startsWith(localRoot.normalize())) {
                    throw new IllegalArgumentException("unsafe local path");
                }
                if (Files.isRegularFile(file)) {
                    try {
                        log.info("GitHub 不可用，改用本机文件 {}", file);
                        return Files.readString(file, StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new IllegalArgumentException("read local failed: " + e.getMessage());
                    }
                }
            }
            throw githubEx;
        }
    }

    private Path resolveLocalReqForgeRoot() {
        List<Path> candidates = new ArrayList<>();
        if (configuredLocalPath != null && !configuredLocalPath.isBlank()) {
            candidates.add(Path.of(configuredLocalPath.trim()));
        }
        String prop = System.getProperty("tepeu.reqforge.local-path");
        if (prop != null && !prop.isBlank()) {
            candidates.add(Path.of(prop.trim()));
        }
        String env = System.getenv("TEPEU_REQFORGE_PATH");
        if (env != null && !env.isBlank()) {
            candidates.add(Path.of(env.trim()));
        }
        Path userDir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        candidates.add(userDir.resolve("../ReqForge").normalize());
        candidates.add(userDir.getParent() != null ? userDir.getParent().resolve("ReqForge") : null);
        candidates.add(Path.of("E:/work/ReqForge"));
        for (Path p : candidates) {
            if (p != null && Files.isDirectory(p) && Files.isRegularFile(p.resolve("core/skills/dev-builder/SKILL.md"))) {
                return p.toAbsolutePath().normalize();
            }
        }
        return null;
    }

    public record PackResult(int installed, int failed, List<String> errors, List<Skill> skills) {}

    private record PackItem(String path, String fallbackName, boolean enable) {}

    private Skill saveParsed(String workspaceId, String nameHint, String content) {
        return saveParsed(workspaceId, nameHint, content, false);
    }

    private Skill saveParsed(String workspaceId, String nameHint, String content, boolean enabled) {
        byte[] raw = content.getBytes(StandardCharsets.UTF_8);
        if (raw.length > MAX_SKILL_CONTENT_BYTES) {
            throw new IllegalArgumentException("skill content too large (max " + MAX_SKILL_CONTENT_BYTES + " bytes)");
        }
        ParsedSkill parsed = parseMarkdown(content.trim());
        String name = (nameHint != null && !nameHint.isBlank())
                ? nameHint.trim()
                : (parsed.name != null ? parsed.name : "未命名技能");
        String slug = slugify(parsed.name != null ? parsed.name : name);
        if (BUILTIN_SLUG.equals(slug)) {
            slug = slug + "-custom";
        }
        Optional<Skill> existing = repository.findByWorkspaceAndSlug(workspaceId, slug);
        if (existing.isPresent()) {
            Skill s = existing.get();
            if (s.isBuiltin()) {
                throw new IllegalArgumentException("Cannot overwrite builtin skill: " + slug);
            }
            s.setName(name);
            s.setDescription(parsed.description);
            s.setContent(parsed.body);
            // 保留原 enabled；若调用方要求启用则打开
            if (enabled) {
                s.setEnabled(true);
            }
            return repository.save(s);
        }
        Skill s = new Skill();
        s.setWorkspaceId(workspaceId);
        s.setSlug(slug);
        s.setName(name);
        s.setDescription(parsed.description);
        s.setContent(parsed.body);
        s.setEnabled(enabled);
        s.setBuiltin(false);
        return repository.save(s);
    }

    static URI validateRemoteUrl(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid url");
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("url must be http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("url host is required");
        }
        return uri;
    }

    private byte[] download(URI uri) {
        try {
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Tepeu-SkillInstaller/0.1")
                    .GET()
                    .build();
            HttpResponse<InputStream> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                throw new IllegalArgumentException("download failed: HTTP " + resp.statusCode());
            }
            try (InputStream in = resp.body()) {
                return readLimited(in, MAX_PACKAGE_BYTES);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("skill download failed from {}: {}", uri, e.toString());
            throw new IllegalArgumentException("download failed: " + e.getMessage());
        }
    }

    private static byte[] readLimited(InputStream in, int max) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        int total = 0;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > max) {
                throw new IllegalArgumentException("download too large (max " + max + " bytes)");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static boolean looksLikeZip(byte[] bytes) {
        return bytes.length >= 4
                && bytes[0] == 'P' && bytes[1] == 'K'
                && (bytes[2] == 3 || bytes[2] == 5 || bytes[2] == 7);
    }

    /**
     * 从 ZIP 提取技能正文：优先最浅层的 SKILL.md；再追加 references 下若干 .md。
     */
    static String extractSkillMarkdownFromZip(byte[] zipBytes) {
        record Entry(String name, String text, int depth) {}
        List<Entry> skillMds = new ArrayList<>();
        List<Entry> refs = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/');
                if (name.contains("..")) {
                    throw new IllegalArgumentException("zip contains unsafe path: " + name);
                }
                String base = name.substring(name.lastIndexOf('/') + 1);
                if (!base.toLowerCase(Locale.ROOT).endsWith(".md")) {
                    continue;
                }
                byte[] data = readLimited(zis, MAX_SKILL_CONTENT_BYTES);
                String text = new String(data, StandardCharsets.UTF_8);
                int depth = (int) name.chars().filter(c -> c == '/').count();
                if (base.equalsIgnoreCase("SKILL.md")) {
                    skillMds.add(new Entry(name, text, depth));
                } else if (name.toLowerCase(Locale.ROOT).contains("/references/")
                        || name.toLowerCase(Locale.ROOT).startsWith("references/")) {
                    refs.add(new Entry(name, text, depth));
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalArgumentException("invalid zip: " + e.getMessage());
        }
        if (skillMds.isEmpty()) {
            throw new IllegalArgumentException("ZIP 中未找到 SKILL.md");
        }
        skillMds.sort(Comparator.comparingInt(Entry::depth).thenComparing(Entry::name));
        Entry main = skillMds.get(0);
        StringBuilder sb = new StringBuilder(main.text().trim());
        refs.sort(Comparator.comparing(Entry::name));
        int budget = MAX_SKILL_CONTENT_BYTES - sb.length();
        for (Entry r : refs) {
            if (budget < 64) break;
            String chunk = "\n\n---\n<!-- " + r.name() + " -->\n" + r.text().trim();
            if (chunk.length() > budget) {
                sb.append(chunk, 0, budget).append("\n...[truncated]");
                break;
            }
            sb.append(chunk);
            budget -= chunk.length();
        }
        return sb.toString();
    }

    public Optional<Skill> setEnabled(String id, boolean enabled) {
        Optional<Skill> opt = repository.findById(id);
        if (opt.isEmpty()) {
            return Optional.empty();
        }
        repository.updateEnabled(id, enabled);
        return repository.findById(id);
    }

    public boolean delete(String id) {
        Optional<Skill> opt = repository.findById(id);
        if (opt.isEmpty()) {
            return false;
        }
        if (opt.get().isBuiltin()) {
            throw new IllegalStateException("Cannot delete builtin skill");
        }
        return repository.deleteById(id);
    }

    /**
     * 按用户消息中的 {@code /slug}、{@code @slug}（或显式 skillRefs）组装本轮技能提示。
     * 不再把「常用/启用」技能自动注入每一轮。
     */
    public Optional<String> buildInvokedSkillsPrompt(String workspaceId, String userMessage, List<String> skillRefs) {
        List<Skill> invoked = resolveInvokedSkills(workspaceId, userMessage, skillRefs);
        if (invoked.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder sb = new StringBuilder();
        sb.append("用户通过 /技能 或 @技能 调用了以下技能。请严格遵循其指引，并结合可用工具（list_files、read_file、write_file、run_command）完成本轮任务。\n\n");
        int budget = MAX_ENABLED_CHARS;
        for (Skill s : invoked) {
            String block = "### Skill: " + s.getName() + " (/" + s.getSlug() + ")\n"
                    + s.getContent().trim() + "\n\n";
            if (block.length() > budget) {
                sb.append(block, 0, Math.max(0, budget));
                sb.append("\n...[skills truncated]\n");
                break;
            }
            sb.append(block);
            budget -= block.length();
        }
        return Optional.of(sb.toString().trim());
    }

    /** 解析本轮调用的技能（去重，保持提及顺序）。 */
    public List<Skill> resolveInvokedSkills(String workspaceId, String userMessage, List<String> skillRefs) {
        if (workspaceId == null || workspaceId.isBlank()) {
            return List.of();
        }
        List<Skill> installed = repository.findByWorkspaceId(workspaceId);
        if (installed.isEmpty()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        if (skillRefs != null) {
            for (String r : skillRefs) {
                if (r != null && !r.isBlank()) {
                    tokens.add(r.trim());
                }
            }
        }
        if (userMessage != null && !userMessage.isBlank()) {
            Matcher m = SKILL_MENTION.matcher(userMessage);
            while (m.find()) {
                tokens.add(m.group(2));
            }
        }
        if (tokens.isEmpty()) {
            return List.of();
        }
        List<Skill> out = new ArrayList<>();
        for (String token : tokens) {
            findSkillByToken(installed, token).ifPresent(s -> {
                if (out.stream().noneMatch(x -> x.getId().equals(s.getId()))) {
                    out.add(s);
                }
            });
        }
        return out;
    }

    /** 判断 token 是否匹配已安装技能（用于从 @ 文件引用里剔除技能名）。 */
    public boolean isInstalledSkillToken(String workspaceId, String token) {
        if (workspaceId == null || workspaceId.isBlank() || token == null || token.isBlank()) {
            return false;
        }
        return findSkillByToken(repository.findByWorkspaceId(workspaceId), token).isPresent();
    }

    static Optional<Skill> findSkillByToken(List<Skill> installed, String token) {
        String key = token.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return Optional.empty();
        }
        for (Skill s : installed) {
            if (s.getSlug() != null && s.getSlug().equalsIgnoreCase(key)) {
                return Optional.of(s);
            }
            if (s.getName() != null && s.getName().equalsIgnoreCase(key)) {
                return Optional.of(s);
            }
            if (s.getName() != null && slugify(s.getName()).equals(key)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }

    /** 解析 YAML frontmatter；允许文件头有 HTML 注释（ReqForge 风格） */
    static ParsedSkill parseMarkdown(String raw) {
        String cleaned = HTML_COMMENT.matcher(raw).replaceAll("").trim();
        Matcher m = FRONTMATTER.matcher(cleaned);
        if (!m.matches()) {
            return new ParsedSkill(null, null, raw);
        }
        String fm = m.group(1);
        String body = m.group(2) != null ? m.group(2).trim() : "";
        String name = null;
        String desc = null;
        Matcher nm = FM_NAME.matcher(fm);
        if (nm.find()) {
            name = nm.group(1).trim();
        }
        Matcher dm = FM_DESC.matcher(fm);
        if (dm.find()) {
            desc = dm.group(1).trim();
        }
        if (body.isEmpty()) {
            body = cleaned;
        }
        return new ParsedSkill(name, desc, body);
    }

    static String slugify(String name) {
        String s = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\u4e00-\\u9fff]+", "-")
                .replaceAll("^-|-$", "");
        if (s.isEmpty()) {
            return "skill";
        }
        if (s.length() > 64) {
            s = s.substring(0, 64);
        }
        return s;
    }

    record ParsedSkill(String name, String description, String body) {}

    private static final String BUILTIN_CONTENT = """
            # 编程助手

            你在 Tepeu 工作区里协助用户写代码、运行和修错。

            ## 工具
            - `list_files`：查看目录
            - `read_file`：读文件
            - `write_file`：创建或覆盖文件（路径相对工作区，如 `/hello.py`）
            - `run_command`：在工作区根目录执行命令（如 `python hello.py`、`npm test`、`dir`）

            ## 工作方式
            1. 先弄清目标；不确定就问一句
            2. 需要改代码时用 `write_file`，不要只口头贴大段代码而不落盘
            3. 改完用 `run_command` 验证；失败则读报错再修，循环直到通过或说明卡住原因
            4. 命令要短、可重复；不要执行危险的系统破坏命令
            5. 用简洁中文汇报：做了什么、结果如何、文件路径在哪
            """;
}
