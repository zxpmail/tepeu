package com.tepeu.service;

import com.tepeu.repository.WorkspaceRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 工作区目录文件变更监听（JDK WatchService）。
 * <p>Phase 12：外部/后台进程修改工作区文件后，经 SSE 推送 {@code file_changed}（带
 * workspaceId），前端按当前工作区过滤自动刷新。监听全部 workspace 根目录（而非仅当前
 * 工作区），由前端过滤——比「随切换启停」更简单健壮，效果等价。
 * <p>复用前端 {@code workspaceEventBus}（WorkspaceEvents.tsx）与 ChatController 的 SSE
 * 事件形状：{@code {type:"file_changed", path, workspaceId, operation}}。
 */
@Component
public class FileWatcherService {

    private static final Logger log = LoggerFactory.getLogger(FileWatcherService.class);

    /** 跳过这些目录名的子目录注册，防 node_modules 等刷爆 SSE。 */
    private static final Set<String> IGNORED_DIRS = Set.of(
            ".git", "node_modules", "target", "dist", ".claude", ".forge", ".idea", ".vscode");

    private final WorkspaceRepository workspaceRepository;
    private final ObjectMapper objectMapper;

    private volatile WatchService watchService;
    private volatile Thread watcherThread;
    private volatile boolean running;

    /** workspaceId → 已监听根目录（绝对路径） */
    private final Map<String, Path> workspaceRoots = new ConcurrentHashMap<>();
    /** WatchKey → 被监听目录 */
    private final Map<WatchKey, Path> keyDirs = new ConcurrentHashMap<>();
    /** SSE 适配器 + 测试监听 */
    private final CopyOnWriteArrayList<Consumer<Map<String, Object>>> listeners = new CopyOnWriteArrayList<>();

    public FileWatcherService(WorkspaceRepository workspaceRepository, ObjectMapper objectMapper) {
        this.workspaceRepository = workspaceRepository;
        this.objectMapper = objectMapper;
    }

    /** 启动：注册既有 workspace + 启动守护线程。建表（jdbcTemplate Bean）先于本方法执行。 */
    @PostConstruct
    void start() {
        for (var w : workspaceRepository.findAll()) {
            String root = w.getRootPath();
            if (root == null || root.isBlank()) {
                root = "workspaces/" + w.getId();
            }
            try {
                registerWorkspace(w.getId(), root);
            } catch (RuntimeException e) {
                log.warn("文件监听注册失败 workspace={}: {}", w.getId(), e.getMessage());
            }
        }
        running = true;
        Thread t = new Thread(this::watchLoop, "tepeu-file-watcher");
        t.setDaemon(true);
        t.start();
        watcherThread = t;
    }

    @PreDestroy
    void shutdown() {
        running = false;
        WatchService ws = watchService;
        if (ws != null) {
            try {
                ws.close();
            } catch (IOException e) {
                log.debug("WatchService close: {}", e.getMessage());
            }
        }
        Thread t = watcherThread;
        if (t != null) {
            t.interrupt();
            try {
                t.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ---- 注册 / 注销 ----

    /** 注册一个 workspace 根目录（连同所有子目录）到 WatchService。 */
    public void registerWorkspace(String workspaceId, String rootPath) {
        Path root = resolveRoot(rootPath);
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("无法创建工作区目录: " + root, e);
        }
        Path existing = workspaceRoots.get(workspaceId);
        if (existing != null && !existing.equals(root)) {
            unregisterRoot(existing);
        }
        workspaceRoots.put(workspaceId, root);
        registerRecursive(root);
    }

    /** 注销一个 workspace：取消该根下所有 WatchKey。 */
    public void unregisterWorkspace(String workspaceId) {
        Path root = workspaceRoots.remove(workspaceId);
        if (root != null) {
            unregisterRoot(root);
        }
    }

    /** 解析磁盘根（相对 user.dir；绝对路径时首参被忽略，与 WorkspaceService 同语义）。 */
    private static Path resolveRoot(String rootPath) {
        return Paths.get(System.getProperty("user.dir"), rootPath).normalize().toAbsolutePath();
    }

    private void unregisterRoot(Path root) {
        keyDirs.entrySet().removeIf(e -> {
            if (e.getValue().startsWith(root)) {
                e.getKey().cancel();
                return true;
            }
            return false;
        });
    }

    /** 注册目录及其所有子目录（跳过忽略目录名）。 */
    private void registerRecursive(Path dir) {
        if (shouldIgnore(dir.getFileName())) {
            return;
        }
        register(dir);
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isDirectory)
                    .filter(p -> !p.equals(dir))
                    .filter(p -> !shouldIgnore(p.getFileName()))
                    .forEach(this::register);
        } catch (IOException e) {
            log.debug("递归注册目录失败 {}: {}", dir, e.getMessage());
        }
    }

    private void register(Path dir) {
        try {
            WatchKey key = dir.register(watchService(),
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            keyDirs.put(key, dir);
        } catch (IOException e) {
            // 目录刚被并发删除等；跳过即可
            log.debug("注册监听失败 {}: {}", dir, e.getMessage());
        }
    }

    private static boolean shouldIgnore(Path name) {
        return name != null && IGNORED_DIRS.contains(name.toString());
    }

    private synchronized WatchService watchService() {
        if (watchService == null) {
            try {
                watchService = FileSystems.getDefault().newWatchService();
            } catch (IOException e) {
                throw new RuntimeException("无法创建 WatchService", e);
            }
        }
        return watchService;
    }

    // ---- 监听循环 ----

    private void watchLoop() {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (java.nio.file.ClosedWatchServiceException e) {
                return;
            }
            processKey(key);
        }
    }

    private void processKey(WatchKey key) {
        Path dir = keyDirs.get(key);
        if (dir == null) {
            key.cancel();
            key.reset();
            return;
        }
        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            if (kind == StandardWatchEventKinds.OVERFLOW) {
                continue;
            }
            Path full = dir.resolve((Path) event.context()).toAbsolutePath().normalize();
            if (kind == StandardWatchEventKinds.ENTRY_CREATE
                    && Files.isDirectory(full)
                    && !shouldIgnore(full.getFileName())) {
                // 新建子目录：注册它（含其已有子目录）
                registerRecursive(full);
            }
            String workspaceId = findWorkspaceId(full);
            if (workspaceId == null) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", "file_changed");
            payload.put("path", toRelPath(workspaceRoots.get(workspaceId), full));
            payload.put("workspaceId", workspaceId);
            payload.put("operation", operationName(kind));
            broadcast(payload);
        }
        boolean valid = key.reset();
        if (!valid) {
            keyDirs.remove(key);
        }
    }

    /** 最长前缀匹配 workspaceId；无匹配返回 null。 */
    private String findWorkspaceId(Path full) {
        String best = null;
        int bestLen = -1;
        for (var e : workspaceRoots.entrySet()) {
            if (full.startsWith(e.getValue())) {
                int len = e.getValue().getNameCount();
                if (len > bestLen) {
                    bestLen = len;
                    best = e.getKey();
                }
            }
        }
        return best;
    }

    private static String toRelPath(Path root, Path full) {
        Path rel = root.relativize(full);
        if (rel.toString().isEmpty()) {
            return "/";
        }
        String s = rel.toString().replace('\\', '/');
        return s.startsWith("/") ? s : "/" + s;
    }

    private static String operationName(WatchEvent.Kind<?> kind) {
        if (kind == StandardWatchEventKinds.ENTRY_CREATE) return "create";
        if (kind == StandardWatchEventKinds.ENTRY_MODIFY) return "modify";
        if (kind == StandardWatchEventKinds.ENTRY_DELETE) return "delete";
        return "other";
    }

    // ---- 广播 / SSE ----

    /**
     * 注册一个 SSE 订阅；连接完成/超时/出错时自动移除。
     * 关联：FileEventsController（GET /api/events）。
     */
    public void subscribe(SseEmitter emitter) {
        Consumer<Map<String, Object>> listener = payload -> sendJson(emitter, payload);
        listeners.add(listener);
        emitter.onCompletion(() -> listeners.remove(listener));
        emitter.onTimeout(() -> listeners.remove(listener));
        emitter.onError(t -> listeners.remove(listener));
    }

    private void sendJson(SseEmitter emitter, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            emitter.send(SseEmitter.event().name("message").data(json));
        } catch (IOException | RuntimeException e) {
            log.debug("SSE 推送失败（客户端断开?）: {}", e.getMessage());
        }
    }

    private void broadcast(Map<String, Object> payload) {
        for (Consumer<Map<String, Object>> listener : listeners) {
            try {
                listener.accept(payload);
            } catch (RuntimeException e) {
                log.debug("广播异常被忽略: {}", e.getMessage());
            }
        }
    }

    // ---- 测试钩子（包内可见，供 FileWatcherServiceTest 确定性排空事件） ----

    /** 排空当前所有待处理 WatchKey；单测手动调用代替守护线程。 */
    void pollPendingEvents() {
        WatchService ws = watchService;
        if (ws == null) {
            return;
        }
        WatchKey key;
        while ((key = ws.poll()) != null) {
            processKey(key);
        }
    }

    /** 直接捕获广播 payload（单测断言）。 */
    void addListener(Consumer<Map<String, Object>> listener) {
        listeners.add(listener);
    }
}
