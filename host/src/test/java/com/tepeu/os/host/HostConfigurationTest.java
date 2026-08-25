package com.tepeu.os.host;

import com.tepeu.os.llm.FakeLlmTransport;
import com.tepeu.os.llm.ProtocolFamily;
import com.tepeu.os.orchestration.PromptAssembly;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostConfigurationTest {

    @Test
    void openaiDefaultModelWhenAnthropicDefaultConfigured() {
        TepeuHostProperties props = new TepeuHostProperties();
        props.setModel(HostConfiguration.DEFAULT_ANTHROPIC_MODEL);
        assertEquals(HostConfiguration.DEFAULT_OPENAI_MODEL,
                HostConfiguration.resolveModel(props, ProtocolFamily.OPENAI));
    }

    @Test
    void explicitFamilyOverridesTransport() {
        TepeuHostProperties props = new TepeuHostProperties();
        props.setFamily("openai");
        assertEquals(ProtocolFamily.OPENAI,
                HostConfiguration.resolveFamily(props, new FakeLlmTransport()));
    }

    @Test
    void assemblePromptRegistersBaseOnce() {
        PromptAssembly prompts = new PromptAssembly();
        var first = HostConfiguration.assemblePrompt(prompts, 500);
        assertTrue(first.includedIds().contains("base"));
        assertTrue(first.system().contains("Tepeu"));
        var second = HostConfiguration.assemblePrompt(prompts, 500);
        assertEquals(first.system(), second.system());
    }
}
