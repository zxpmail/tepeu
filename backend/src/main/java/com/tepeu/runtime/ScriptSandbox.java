package com.tepeu.runtime;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * GraalJS 技能脚本沙箱 — 限时、无任意主机 FS，仅暴露 {@link WorkspaceScriptFs}。
 * 关联：RunSkillScriptTool、ADR-015。
 */
@Component
public class ScriptSandbox {

    private static final Logger log = LoggerFactory.getLogger(ScriptSandbox.class);

    private final long defaultTimeoutMs;

    public ScriptSandbox(
            @Value("${tepeu.runtime.script-timeout-ms:5000}") long defaultTimeoutMs) {
        this.defaultTimeoutMs = defaultTimeoutMs > 0 ? defaultTimeoutMs : 5000;
    }

    public long defaultTimeoutMs() {
        return defaultTimeoutMs;
    }

    /**
     * 在 workspace 沙箱中执行 JS；返回脚本最后表达式或 {@code result} 绑定的字符串。
     *
     * @throws ScriptTimeoutException 超时强制中断
     * @throws ScriptSandboxException 脚本/安全错误
     */
    public String eval(Path workspaceRoot, String source, String inputJson, Long timeoutMs) {
        if (workspaceRoot == null) {
            throw new ScriptSandboxException("workspace root required");
        }
        if (source == null || source.isBlank()) {
            throw new ScriptSandboxException("script source required");
        }
        long limit = timeoutMs != null && timeoutMs > 0 ? timeoutMs : defaultTimeoutMs;
        Path root = workspaceRoot.toAbsolutePath().normalize();

        AtomicReference<Context> ctxRef = new AtomicReference<>();
        ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "tepeu-script-sandbox");
            t.setDaemon(true);
            return t;
        });
        Future<String> future = pool.submit(() -> runInContext(root, source, inputJson, ctxRef));
        try {
            return future.get(limit, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            Context ctx = ctxRef.get();
            if (ctx != null) {
                try {
                    ctx.close(true);
                } catch (Exception closeEx) {
                    log.debug("强制关闭脚本 Context: {}", closeEx.toString());
                }
            }
            future.cancel(true);
            throw new ScriptTimeoutException("脚本超时（" + limit + " ms），已强制中断");
        } catch (ExecutionException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            if (c instanceof ScriptSandboxException sse) {
                throw sse;
            }
            if (c instanceof SecurityException se) {
                throw new ScriptSandboxException(se.getMessage(), se);
            }
            throw new ScriptSandboxException(c.getMessage() != null ? c.getMessage() : c.toString(), c);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ScriptSandboxException("脚本执行被中断");
        } finally {
            pool.shutdownNow();
            Context ctx = ctxRef.get();
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (Exception ignored) {
                    // already closed on timeout
                }
            }
        }
    }

    private String runInContext(
            Path root, String source, String inputJson, AtomicReference<Context> ctxRef) {
        HostAccess hostAccess = HostAccess.newBuilder()
                .allowAccessAnnotatedBy(HostAccess.Export.class)
                .build();
        try (Context context = Context.newBuilder("js")
                .allowHostAccess(hostAccess)
                .allowHostClassLookup(s -> false)
                .allowIO(false)
                .allowNativeAccess(false)
                .allowCreateThread(false)
                .allowCreateProcess(false)
                .allowExperimentalOptions(true)
                .option("engine.WarnInterpreterOnly", "false")
                .build()) {
            ctxRef.set(context);
            context.getBindings("js").putMember("fs", new WorkspaceScriptFs(root));
            context.getBindings("js").putMember(
                    "input", inputJson != null ? inputJson : "");
            org.graalvm.polyglot.Value result = context.eval("js", source);
            org.graalvm.polyglot.Value bound = context.getBindings("js").getMember("result");
            if (bound != null && !bound.isNull()) {
                return bound.toString();
            }
            if (result != null && !result.isNull()) {
                return result.toString();
            }
            return "ok";
        } catch (PolyglotException e) {
            if (e.isCancelled()) {
                throw new ScriptTimeoutException("脚本已被取消");
            }
            throw new ScriptSandboxException(summarizePolyglot(e), e);
        }
    }

    private static String summarizePolyglot(PolyglotException e) {
        String msg = e.getMessage();
        if (msg != null && !msg.isBlank()) {
            return msg;
        }
        return e.isGuestException() ? "script error" : e.toString();
    }

    /** 脚本业务/安全错误 */
    public static class ScriptSandboxException extends RuntimeException {
        public ScriptSandboxException(String message) { super(message); }
        public ScriptSandboxException(String message, Throwable cause) { super(message, cause); }
    }

    /** 超时 */
    public static class ScriptTimeoutException extends ScriptSandboxException {
        public ScriptTimeoutException(String message) { super(message); }
    }
}
