package com.tepeu.llm.fake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tepeu.llm.gateway.LlmMessage;
import com.tepeu.llm.gateway.Role;
import com.tepeu.syscall.SyscallResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class FakeBackendTest {

    @Test
    void repliesFixedTextWithFixedUsage() {
        FakeBackend backend = new FakeBackend();
        SyscallResult a = backend.generate(List.of(new LlmMessage(Role.USER, "hi")));
        SyscallResult b = backend.generate(List.of());
        assertTrue(a.ok());
        assertEquals(FakeBackend.REPLY, a.output());
        assertEquals(a.output(), b.output());
        assertEquals(a.usage(), b.usage());
        assertEquals(2, a.usage().orElseThrow().totalTokens());
    }
}
