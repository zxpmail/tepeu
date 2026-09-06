package com.tepeu.os.policy;

import com.tepeu.os.policy.local.CommandLineNormalizer;
import com.tepeu.os.syscall.Syscall;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandLineNormalizerTest {

    @Test
    void collapsesWhitespaceAndLowercases() {
        String norm = CommandLineNormalizer.normalize(new Syscall("execution.proc.spawn",
                Map.of("path", "Bin\\Run.EXE", "args", "  Foo   BAR  ")));
        assertEquals("bin/run.exe foo bar", norm);
    }

    @Test
    void ignoresNonProcSyscalls() {
        assertEquals("", CommandLineNormalizer.normalize(new Syscall("echo", Map.of("text", "x"))));
    }
}
