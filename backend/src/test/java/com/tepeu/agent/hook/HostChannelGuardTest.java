package com.tepeu.agent.hook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REST/终端宿主通道门禁。 */
class HostChannelGuardTest {

    private ApprovalStore store;
    private HostChannelGuard guard;

    @BeforeEach
    void setUp() {
        store = new ApprovalStore(0L);
        guard = new HostChannelGuard(new DangerousToolHook(), store);
    }

    @Test
    void restWrite_allowedWithoutApproval() {
        HostChannelGuard.GateResult r = guard.check(
                "rest-ws1", "rest_write_file", "{\"path\":\"/a.txt\"}", false);
        assertTrue(r.allowed());
    }

    @Test
    void restDelete_allowedInSandbox() {
        HostChannelGuard.GateResult r = guard.check(
                "rest-ws1", "rest_delete_file", "{\"path\":\"/a.txt\"}", false);
        assertTrue(r.allowed());
    }

    @Test
    void terminalSafeCommand_allowed() {
        HostChannelGuard.GateResult r = guard.check(
                "terminal-1", "terminal_shell", "{\"command\":\"dir\"}", false);
        assertTrue(r.allowed());
    }

    @Test
    void terminalDangerous_needsApproval() {
        HostChannelGuard.GateResult r = guard.check(
                "terminal-1", "terminal_shell", "{\"command\":\"del important.txt\"}", false);
        assertFalse(r.allowed());
        assertEquals("APPROVAL_REQUIRED", r.code());
    }

    @Test
    void terminalCatastrophic_denied() {
        HostChannelGuard.GateResult r = guard.check(
                "terminal-1", "terminal_shell", "{\"command\":\"format C:\"}", false);
        assertFalse(r.allowed());
        assertEquals("DENIED", r.code());
    }
}
