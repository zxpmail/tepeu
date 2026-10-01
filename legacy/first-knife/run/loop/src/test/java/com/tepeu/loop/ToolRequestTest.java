package com.tepeu.loop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolRequestTest {

    @Test
    void parsesFirstLineOnly() {
        ToolRequest r = ToolRequest.parse("@tool execution.fs.read path=a.txt;flag=1\nnext line")
                .orElseThrow();
        assertEquals("execution.fs.read", r.name());
        assertEquals(Map.of("path", "a.txt", "flag", "1"), r.args());
        assertEquals("@tool execution.fs.read path=a.txt;flag=1", r.line());
    }

    @Test
    void noArgsMeansEmptyMap() {
        ToolRequest r = ToolRequest.parse("@tool execution.sandbox.probe").orElseThrow();
        assertEquals("execution.sandbox.probe", r.name());
        assertEquals(Map.of(), r.args());
    }

    @Test
    void malformedIsPlainAnswer() {
        assertTrue(ToolRequest.parse("plain answer").isEmpty());
        assertTrue(ToolRequest.parse("@tool").isEmpty());
        assertTrue(ToolRequest.parse("@tool nopair k;v=1").isEmpty());
        assertTrue(ToolRequest.parse("@tool ok =v").isEmpty());
        assertTrue(ToolRequest.parse("@tool ok bad;end=1").isEmpty());
        assertTrue(ToolRequest.parse(null).isEmpty());
    }
}
