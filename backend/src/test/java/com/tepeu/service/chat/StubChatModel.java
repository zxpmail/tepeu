package com.tepeu.service.chat;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Minimal {@link ChatModel} stub for unit tests. Implements {@link ChatModel} (which extends
 * {@code StreamingChatModel}) by satisfying the two abstract methods ({@code call(Prompt)} and
 * {@code stream(Prompt)}) and exposing a tag via {@link ChatOptions#getModel()} so tests can
 * identify which provider branch produced it. No network access.
 */
class StubChatModel implements ChatModel {

    private final String tag;

    StubChatModel(String tag) {
        this.tag = tag;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(tag))));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.just(call(prompt));
    }

    @Override
    public ChatOptions getDefaultOptions() {
        // ChatOptions has many abstract getters; use the provided builder which fills defaults.
        return ChatOptions.builder().model(tag).build();
    }

    @Override
    public String call(String message) {
        return tag;
    }

    @Override
    public String call(Message... messages) {
        return tag;
    }
}
