package com.tepeu.agent.hook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** ApprovalStore：pending、按参数授权、超时过期。 */
class ApprovalStoreTest {

    private ApprovalStore store;

    @BeforeEach
    void setUp() {
        store = new ApprovalStore(0L);
    }

    @Test
    void approve_grantsExactArgsOnly() {
        String args = "{\"command\":\"dir\"}";
        ApprovalStore.Approval pending = store.createPending("sess-1", "run_command", args);
        assertFalse(store.hasSessionGrant("sess-1", "run_command", args));

        var decided = store.decide(pending.id(), true);
        assertTrue(decided.isPresent());
        assertEquals(ApprovalStore.Status.APPROVED, decided.get().status());
        assertTrue(store.hasSessionGrant("sess-1", "run_command", args));
        assertFalse(store.hasSessionGrant("sess-1", "run_command", "{\"command\":\"del x\"}"));
    }

    @Test
    void createPending_sameSessionToolArgs_reuses() {
        ApprovalStore.Approval first = store.createPending("sess-1", "run_command", "{\"a\":1}");
        ApprovalStore.Approval second = store.createPending("sess-1", "run_command", "{\"a\":1}");
        assertEquals(first.id(), second.id());
    }

    @Test
    void createPending_differentArgs_newPending() {
        ApprovalStore.Approval first = store.createPending("sess-1", "run_command", "{\"a\":1}");
        ApprovalStore.Approval second = store.createPending("sess-1", "run_command", "{\"a\":2}");
        assertNotEquals(first.id(), second.id());
    }

    @Test
    void deny_doesNotGrant() {
        ApprovalStore.Approval pending = store.createPending("sess-1", "run_command", "{}");
        var decided = store.decide(pending.id(), false);
        assertTrue(decided.isPresent());
        assertEquals(ApprovalStore.Status.DENIED, decided.get().status());
        assertFalse(store.hasSessionGrant("sess-1", "run_command", "{}"));
    }

    @Test
    void decideTwice_returnsEmpty() {
        ApprovalStore.Approval pending = store.createPending("sess-1", "run_command", "{}");
        assertTrue(store.decide(pending.id(), true).isPresent());
        assertTrue(store.decide(pending.id(), true).isEmpty());
    }

    @Test
    void unknownId_returnsEmpty() {
        assertTrue(store.decide("nope", true).isEmpty());
    }

    @Test
    void enableAutonomous_marksSession() {
        assertFalse(store.isAutonomous("sess-auto"));
        store.enableAutonomous("sess-auto");
        assertTrue(store.isAutonomous("sess-auto"));
    }

    @Test
    void expire_marksExpired() {
        ApprovalStore.Approval pending = store.createPending("sess-1", "run_command", "{}");
        store.expire(pending.id());
        assertEquals(ApprovalStore.Status.EXPIRED, store.find(pending.id()).orElseThrow().status());
        assertTrue(store.decide(pending.id(), true).isEmpty());
    }

    @Test
    void awaitDecision_timeout_expires() {
        ApprovalStore waiting = new ApprovalStore(1L);
        ApprovalStore.Approval pending = waiting.createPending("sess-1", "run_command", "{}");
        assertTrue(waiting.awaitDecision(pending.id()).isEmpty());
        assertEquals(ApprovalStore.Status.EXPIRED, waiting.find(pending.id()).orElseThrow().status());
    }
}
