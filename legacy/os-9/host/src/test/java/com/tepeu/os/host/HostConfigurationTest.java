package com.tepeu.os.host;

import com.tepeu.os.llm.local.FakeLlmTransport;
import com.tepeu.os.llm.ProtocolFamily;
import com.tepeu.os.orchestration.local.PromptAssembly;
import org.junit.jupiter.api.Test;

import java.util.Optional;

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

    @Test
    void baseUrlFromPropertiesBeatsDefault() {
        TepeuHostProperties props = new TepeuHostProperties();
        props.setBaseUrl("https://api.deepseek.com");
        assertEquals("https://api.deepseek.com",
                HostConfiguration.resolveBaseUrl(props, ProtocolFamily.OPENAI));
    }

    @Test
    void explicitOpenaiFamilyWithoutEnvStillResolvesFamily() {
        TepeuHostProperties props = new TepeuHostProperties();
        props.setFamily("openai");
        props.setApiKey("sk-test");
        assertEquals(Optional.of(ProtocolFamily.OPENAI),
                HostConfiguration.resolveTransportFamily(props));
    }
}
