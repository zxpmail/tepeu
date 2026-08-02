package com.tepeu.agent.tool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 会话隔离与 ThreadLocal 上下文。 */
class CommandOutputStoreTest {

    @Test
    void put_isolatesSessions() {
        CommandOutputStore store = new CommandOutputStore();
        store.put("sess-a", "echo a", "output-a");
        store.put("sess-b", "echo b", "output-b");
        assertEquals("output-a", store.get("sess-a").output());
        assertEquals("output-b", store.get("sess-b").output());
        store.clear("sess-a");
        assertNull(store.get("sess-a"));
        assertEquals("output-b", store.get("sess-b").output());
    }

    @Test
    void enter_exit_scopesCurrentKey() {
        CommandOutputStore store = new CommandOutputStore();
        CommandOutputStore.enter("sess-t");
        try {
            assertEquals("sess-t", CommandOutputStore.currentKey());
            store.put("cmd", "via-thread");
            assertEquals("via-thread", store.get().output());
        } finally {
            CommandOutputStore.exit();
        }
        assertEquals(CommandOutputStore.ANON_KEY, CommandOutputStore.currentKey());
    }
}
