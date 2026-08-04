package com.tepeu.controller;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** SlashController 命令行解析。 */
class SlashControllerParseTest {

    @Test
    void parse_withSlashAndArgs() {
        var p = SlashController.parseCommandLine("/schedule list");
        assertEquals("schedule", p.name());
        assertEquals(List.of("list"), p.args());
    }

    @Test
    void parse_bareName() {
        var p = SlashController.parseCommandLine("help");
        assertEquals("help", p.name());
        assertTrue(p.args().isEmpty());
    }

    @Test
    void parse_blank_throws() {
        assertThrows(IllegalArgumentException.class, () -> SlashController.parseCommandLine("  "));
    }
}
