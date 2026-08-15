package com.tepeu.service;

import com.tepeu.repository.WorkspaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 文件监听：create/modify/delete 广播、递归注册子目录、workspace 隔离、忽略目录、注销。
 * 纯单测（不启动守护线程），用 {@link FileWatcherService#pollPendingEvents()} 手动排空事件。
 */
class FileWatcherServiceTest {

    @TempDir
    Path tempDir;

    private FileWatcherService service;
    private List<Map<String, Object>> events;

    @BeforeEach
    void setUp() {
        System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
        service = new FileWatcherService(mock(WorkspaceRepository.class), new ObjectMapper());
        events = new ArrayList<>();
        service.addListener(events::add);
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void createFile_broadcastsFileChanged() throws Exception {
        Path ws = tempDir.resolve("ws1");
        Files.createDirectories(ws);
        service.registerWorkspace("ws1", "ws1");

        Files.writeString(ws.resolve("a.txt"), "hi");
        drain();

        assertTrue(hasEvent("create", "/a.txt", "ws1"), "events=" + events);
    }

    @Test
    void modifyFile_broadcastsModify() throws Exception {
        Path ws = tempDir.resolve("ws1");
        Files.createDirectories(ws);
        service.registerWorkspace("ws1", "ws1");
        Files.writeString(ws.resolve("a.txt"), "hi");
        drain();

        Files.writeString(ws.resolve("a.txt"), "hello world");
        drain();

        assertTrue(hasEvent("modify", "/a.txt", "ws1"), "events=" + events);
    }

    @Test
    void deleteFile_broadcastsDelete() throws Exception {
        Path ws = tempDir.resolve("ws1");
        Files.createDirectories(ws);
        service.registerWorkspace("ws1", "ws1");
        Files.writeString(ws.resolve("a.txt"), "hi");
        drain();

        Files.delete(ws.resolve("a.txt"));
        drain();

        assertTrue(hasEvent("delete", "/a.txt", "ws1"), "events=" + events);
    }

    @Test
    void newSubdirectory_registersRecursively() throws Exception {
        Path ws = tempDir.resolve("ws1");
        Files.createDirectories(ws);
        service.registerWorkspace("ws1", "ws1");

        Files.createDirectories(ws.resolve("sub"));   // 新建目录 → 应被递归注册
        drain();
        Files.writeString(ws.resolve("sub").resolve("b.txt"), "x");
        drain();

        assertTrue(hasEvent("create", "/sub/b.txt", "ws1"), "events=" + events);
    }

    @Test
    void twoWorkspaces_carryCorrectWorkspaceId() throws Exception {
        Path ws1 = tempDir.resolve("ws1");
        Path ws2 = tempDir.resolve("ws2");
        Files.createDirectories(ws1);
        Files.createDirectories(ws2);
        service.registerWorkspace("ws1", "ws1");
        service.registerWorkspace("ws2", "ws2");

        Files.writeString(ws1.resolve("one.txt"), "1");
        Files.writeString(ws2.resolve("two.txt"), "2");
        drain();

        assertTrue(hasEvent("create", "/one.txt", "ws1"), "events=" + events);
        assertTrue(hasEvent("create", "/two.txt", "ws2"), "events=" + events);
        assertFalse(hasEvent("create", "/one.txt", "ws2"), "events=" + events);
        assertFalse(hasEvent("create", "/two.txt", "ws1"), "events=" + events);
    }

    @Test
    void ignoredDir_isNotRegistered() throws Exception {
        Path ws = tempDir.resolve("ws1");
        Files.createDirectories(ws);
        service.registerWorkspace("ws1", "ws1");

        Files.createDirectories(ws.resolve("node_modules"));
        drain();
        Files.writeString(ws.resolve("node_modules").resolve("pkg.js"), "x");
        drain();

        assertFalse(hasEvent("create", "/node_modules/pkg.js", "ws1"), "events=" + events);
    }

    @Test
    void preExistingIgnoredTree_nestedDirsNotRegistered() throws Exception {
        // 注册前已有 .git/objects、node_modules/pkg —— 子目录名不在黑名单，旧逻辑会误注册
        Path ws = tempDir.resolve("ws1");
        Files.createDirectories(ws.resolve(".git").resolve("objects"));
        Files.createDirectories(ws.resolve("node_modules").resolve("pkg"));
        Files.createDirectories(ws.resolve("src"));
        service.registerWorkspace("ws1", "ws1");

        Files.writeString(ws.resolve(".git").resolve("objects").resolve("ab"), "x");
        Files.writeString(ws.resolve("node_modules").resolve("pkg").resolve("index.js"), "y");
        Files.writeString(ws.resolve("src").resolve("ok.txt"), "z");
        drain();

        assertFalse(hasEvent("create", "/.git/objects/ab", "ws1"), "events=" + events);
        assertFalse(hasEvent("create", "/node_modules/pkg/index.js", "ws1"), "events=" + events);
        assertTrue(hasEvent("create", "/src/ok.txt", "ws1"), "events=" + events);
    }

    @Test
    void startWithNoWorkspaces_doesNotCrashWatcherThread() throws Exception {
        // 模拟 @PostConstruct start：零 workspace 时 WatchService 仍可用，后续 register 正常
        service.start();
        Path ws = tempDir.resolve("ws-late");
        Files.createDirectories(ws);
        service.registerWorkspace("ws-late", "ws-late");
        Files.writeString(ws.resolve("late.txt"), "ok");
        drain();
        assertTrue(hasEvent("create", "/late.txt", "ws-late"), "events=" + events);
    }

    @Test
    void unregisterWorkspace_stopsBroadcasting() throws Exception {
        Path ws = tempDir.resolve("ws1");
        Files.createDirectories(ws);
        service.registerWorkspace("ws1", "ws1");
        Files.writeString(ws.resolve("a.txt"), "x");
        drain();
        assertTrue(hasEvent("create", "/a.txt", "ws1"), "events=" + events);

        service.unregisterWorkspace("ws1");
        events.clear();
        Files.writeString(ws.resolve("b.txt"), "y");
        drain();

        assertFalse(hasEvent("create", "/b.txt", "ws1"), "events=" + events);
    }

    @Test
    void repeatedWritesWithinWindow_areCoalescedToSingleEvent() throws Exception {
        Path ws = tempDir.resolve("ws1");
        Files.createDirectories(ws);
        service.registerWorkspace("ws1", "ws1");
        Files.writeString(ws.resolve("a.txt"), "hi");
        drain();
        events.clear();

        // 同一合并窗口内多次写同一文件 → 只广播一条，取最后一次 operation（modify）
        Files.writeString(ws.resolve("a.txt"), "v1");
        Files.writeString(ws.resolve("a.txt"), "v2");
        Files.writeString(ws.resolve("a.txt"), "v3");
        Thread.sleep(200);
        service.pollPendingEvents();
        service.flushPending();

        long modifies = events.stream()
                .filter(e -> "modify".equals(e.get("operation"))).count();
        assertEquals(1, modifies, "events=" + events);
        assertEquals(1, events.size(), "events=" + events);
    }

    private void drain() throws Exception {
        // WatchService 事件由 OS 异步投递，稍等再排空；合并窗口手动 flush
        Thread.sleep(200);
        service.pollPendingEvents();
        service.flushPending();
    }

    private boolean hasEvent(String op, String path, String wsId) {
        return events.stream().anyMatch(e ->
                "file_changed".equals(e.get("type"))
                        && op.equals(e.get("operation"))
                        && path.equals(e.get("path"))
                        && wsId.equals(e.get("workspaceId")));
    }
}
