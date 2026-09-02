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

public final class FsReadHandler implements SyscallHandler {

    private final SandboxPolicy sandbox;

    public FsReadHandler(SandboxPolicy sandbox) {
        this.sandbox = Objects.requireNonNull(sandbox, "sandbox");
    }

    @Override
    public SyscallResult handle(TurnContext ctx, Syscall syscall) {
        Path target = WorkspaceJail.resolve(sandbox.workspaceRoot(), syscall.args().get("path")).orElse(null);
        if (target == null) {
            return SyscallResult.failure("JAIL", "path escapes workspace");
        }
        if (!Files.isRegularFile(target, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            return SyscallResult.failure("NOT_FOUND", "not a file: " + relative(target));
        }
        try {
            return SyscallResult.success(Files.readString(target, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return SyscallResult.failure("IO", String.valueOf(e.getMessage()));
        }
    }

    private String relative(Path target) {
        return sandbox.workspaceRoot().toAbsolutePath().normalize().relativize(target).toString();
    }
}
