package com.tepeu.agent.tool;

import com.tepeu.service.WorkspacePathResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 需要绑定工作区根目录的 Agent 工具共享基类。
 * 处理 bindWorkspace/unbindWorkspace/currentBasePath/resolveSafely，
 * 各工具子类只需暴露 {@code @Tool} 方法。
 *
 * <p>不要提供 public 无参构造：Spring 若误用无参构造，会把根目录钉死在
 * {@code user.dir/workspace}，生成的文件左侧文件树看不见。
 */
public abstract class WorkspaceBoundTool {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceBoundTool.class);

    protected final WorkspacePathResolver pathResolver;
    /** 测试注入固定根；生产为 null */
    protected final Path fixedBasePath;
    /** 当前对话已解析的工作区根（绝对路径；工具回调可能在别的线程） */
    protected final AtomicReference<Path> activeBasePath = new AtomicReference<>();

    protected WorkspaceBoundTool(WorkspacePathResolver pathResolver) {
        this.pathResolver = pathResolver;
        this.fixedBasePath = null;
    }

    /** 测试缝：固定 basePath，不依赖 WorkspaceService */
    protected WorkspaceBoundTool(Path basePath) {
        this.pathResolver = null;
        this.fixedBasePath = basePath.toAbsolutePath().normalize();
    }

    /** 本轮对话开始时绑定工作区，结束时 {@link #unbindWorkspace()} */
    public void bindWorkspace(String workspaceId) {
        if (fixedBasePath != null) {
            activeBasePath.set(fixedBasePath);
            return;
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            activeBasePath.set(null);
            log.warn("{} bindWorkspace: workspaceId 为空", getClass().getSimpleName());
            return;
        }
        Path base = pathResolver.resolveBasePath(workspaceId);
        activeBasePath.set(base);
        log.debug("{} 已绑定 workspaceId={} → {}", getClass().getSimpleName(), workspaceId, base);
    }

    public void unbindWorkspace() {
        activeBasePath.set(null);
    }

    protected Path currentBasePath() {
        if (fixedBasePath != null) {
            return fixedBasePath;
        }
        Path bound = activeBasePath.get();
        if (bound != null) {
            return bound;
        }
        log.warn("{} 在未 bindWorkspace 时被调用，回退到默认工作区根目录", getClass().getSimpleName());
        return pathResolver.resolveBasePath(null);
    }

    protected Path resolveSafely(String path) {
        return WorkspacePathResolver.resolveSafely(currentBasePath(), path);
    }
}
