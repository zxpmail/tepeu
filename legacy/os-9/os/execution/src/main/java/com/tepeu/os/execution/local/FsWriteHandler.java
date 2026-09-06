package com.tepeu.os.execution.local;

import com.tepeu.os.execution.*;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallHandler;
import com.tepeu.os.syscall.SyscallResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class FsWriteHandler implements SyscallHandler {

    private final SandboxPolicy sandbox;

    public FsWriteHandler(SandboxPolicy sandbox) {
        this.sandbox = Objects.requireNonNull(sandbox, "sandbox");
    }

    @Override
    public SyscallResult handle(TurnContext ctx, Syscall syscall) {
        Path target = WorkspaceJail.resolve(sandbox.workspaceRoot(), syscall.args().get("path")).orElse(null);
        if (target == null) {
            return SyscallResult.failure("JAIL", "path escapes workspace");
        }
        String content = syscall.args().getOrDefault("content", "");
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(target, content, StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                    java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.LinkOption.NOFOLLOW_LINKS);
            return SyscallResult.success(relative(target));
        } catch (IOException e) {
            return SyscallResult.failure("IO", String.valueOf(e.getMessage()));
        }
    }

    private String relative(Path target) {
        return sandbox.workspaceRoot().toAbsolutePath().normalize().relativize(target).toString();
    }
}
