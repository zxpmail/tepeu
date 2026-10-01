package com.tepeu.llm.anthropic;

import com.tepeu.llm.gateway.LlmMessage;
import com.tepeu.llm.gateway.Role;
import com.tepeu.syscall.Usage;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnthropicBackendTest {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Test
    void requestCarriesSystemModelMaxTokensAndRoles() {
        String json = AnthropicBackend.buildRequest(List.of(
                new LlmMessage(Role.SYSTEM, "you are tepeu"),
                new LlmMessage(Role.USER, "hi"),
                new LlmMessage(Role.ASSISTANT, "hello"),
                new LlmMessage(Role.USER, "bye")),
                "claude-sonnet-4-6", 4096);
        JsonNode root = MAPPER.readTree(json);
        assertEquals("claude-sonnet-4-6", root.path("model").asString());
        assertEquals(4096, root.path("max_tokens").asInt());
        assertEquals("you are tepeu", root.path("system").asString());
        JsonNode messages = root.path("messages");
        assertEquals(3, messages.size());
        assertEquals("user", messages.get(0).path("role").asString());
        assertEquals("hi", messages.get(0).path("content").asString());
        assertEquals("assistant", messages.get(1).path("role").asString());
        assertEquals("user", messages.get(2).path("role").asString());
    }

    @Test
    void toolResultsMapToUserAndAdjacentMerge() {
        String json = AnthropicBackend.buildRequest(List.of(
                new LlmMessage(Role.USER, "read it"),
                new LlmMessage(Role.TOOL, "file bytes"),
                new LlmMessage(Role.TOOL, "more bytes")),
                "m", 1);
        JsonNode root = MAPPER.readTree(json);
        JsonNode messages = root.path("messages");
        assertEquals(1, messages.size());
        assertEquals("user", messages.get(0).path("role").asString());
        assertEquals("read it\n\nfile bytes\n\nmore bytes",
                messages.get(0).path("content").asString());
    }

    @Test
    void emptyVisibleGivesSystemFreeEmptyMessages() {
        JsonNode root = MAPPER.readTree(AnthropicBackend.buildRequest(List.of(), "m", 1));
        assertEquals(0, root.path("messages").size());
        assertEquals(true, root.path("system").isMissingNode());
    }

    @Test
    void responseTextBlocksConcatenatedAndUsagePassedThrough() {
        AnthropicBackend.Parsed parsed = AnthropicBackend.parseResponse("""
                {"model":"m","stop_reason":"end_turn",
                 "content":[{"type":"text","text":"第一段"},{"type":"tool_use","id":"x","name":"n","input":{}},
                            {"type":"text","text":"第二段"}],
                 "usage":{"input_tokens":10,"output_tokens":5}}
                """);
        assertEquals("第一段\n第二段", parsed.text());
        assertEquals(new Usage(10, 5), parsed.usage());
    }

    @Test
    void constructorRejectsBlankParts() {
        assertThrows(IllegalArgumentException.class, () -> new AnthropicBackend(" ", "https://x", "m"));
        assertThrows(IllegalArgumentException.class, () -> new AnthropicBackend("t", " ", "m"));
        assertThrows(IllegalArgumentException.class, () -> new AnthropicBackend("t", "https://x", " "));
    }
}
