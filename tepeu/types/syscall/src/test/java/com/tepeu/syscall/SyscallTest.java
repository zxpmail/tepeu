package com.tepeu.syscall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class SyscallTest {

    @Test
    void nameRejectsBlank() {
        assertThrows(NullPointerException.class, () -> new Syscall(null, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new Syscall(" ", Map.of()));
    }

    @Test
    void argsCopiedAndNullBecomesEmpty() {
        Map<String, String> raw = new HashMap<>();
        raw.put("path", "/a");
        Syscall call = new Syscall("fs.read", raw);
        raw.put("path", "/b");
        assertEquals("/a", call.args().get("path"));
        assertTrue(new Syscall("fs.read", null).args().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> call.args().put("x", "y"));
    }

    @Test
    void resultSuccessAndFailure() {
        assertTrue(SyscallResult.success("ok").ok());
        SyscallResult fail = SyscallResult.failure("DENIED", "no");
        assertFalse(fail.ok());
        assertEquals(Optional.of("DENIED"), fail.errorCode());
        assertEquals("no", fail.output());
    }

    @Test
    void usageTotalsWithoutPretendCost() {
        Usage usage = new Usage(3, 5);
        assertEquals(8, usage.totalTokens());
        assertTrue(usage.cost().isEmpty());
    }

    @Test
    void argDigestStableForKeyOrderAndEmpty() {
        String a = ArgDigest.of(Map.of("b", "2", "a", "1"));
        String b = ArgDigest.of(Map.of("a", "1", "b", "2"));
        assertEquals(a, b);
        assertEquals(ArgDigest.of(Map.of()), ArgDigest.of(null));
    }
}
