package com.tepeu.os.execution;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.syscall.Syscall;
import com.tepeu.os.syscall.SyscallHandler;
import com.tepeu.os.syscall.SyscallResult;

import java.util.Objects;

public final class SandboxProbeHandler implements SyscallHandler {

    private final SandboxPolicy sandbox;

    public SandboxProbeHandler(SandboxPolicy sandbox) {
        this.sandbox = Objects.requireNonNull(sandbox, "sandbox");
    }

    @Override
    public SyscallResult handle(TurnContext ctx, Syscall syscall) {
        String proc = sandbox.jail().canSpawn() ? "available" : "unavailable";
        return SyscallResult.success(
                "isolation=" + sandbox.isolation().name().toLowerCase()
                        + ";mechanism=" + sandbox.mechanism()
                        + ";proc=" + proc
                        + ";workspace=" + sandbox.workspaceRoot().toAbsolutePath().normalize());
    }
}
