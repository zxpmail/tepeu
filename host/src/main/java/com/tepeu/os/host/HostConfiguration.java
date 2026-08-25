package com.tepeu.os.host;

import com.tepeu.os.compose.MemoryAssembly;
import com.tepeu.os.compose.SqliteAssembly;
import com.tepeu.os.llm.AnthropicHttpTransport;
import com.tepeu.os.llm.FakeLlmTransport;
import com.tepeu.os.llm.LlmTransport;
import com.tepeu.os.llm.LlmTransports;
import com.tepeu.os.llm.OpenAiHttpTransport;
import com.tepeu.os.llm.ProtocolFamily;
import com.tepeu.os.loop.LoopConfig;
import com.tepeu.os.orchestration.AssembledPrompt;
import com.tepeu.os.orchestration.PromptAssembly;
import com.tepeu.os.orchestration.Section;
import com.tepeu.os.session.LedgerMetering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class HostConfiguration {

    static final String DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-4-20250514";
    static final String DEFAULT_OPENAI_MODEL = "gpt-4o-mini";
    static final String BASE_PROMPT =
            "You are Tepeu, a local Agent OS assistant. Be concise and honest about limits.";

    private static final Logger log = LoggerFactory.getLogger(HostConfiguration.class);

    @Bean(destroyMethod = "close")
    MemoryAssembly.Wired kernel(TepeuHostProperties props, LlmTransport transport) {
        return SqliteAssembly.file(props.dataDir(), transport, LedgerMetering.unlimited());
    }

    @Bean
    LlmTransport llmTransport(TepeuHostProperties props) {
        if (props.fakeLlm()) {
            log.info("LLM: fake (tepeu.fake-llm=true)");
            return new FakeLlmTransport();
        }
        return LlmTransports.fromEnv()
                .map(t -> {
                    log.info("LLM: HTTP transport from environment ({})", familyOf(t).name().toLowerCase());
                    return t;
                })
                .orElseGet(() -> {
                    log.warn("No ANTHROPIC_API_KEY / OPENAI_API_KEY; using FakeLlmTransport");
                    return new FakeLlmTransport();
                });
    }

    @Bean
    LoopConfig loopConfig(TepeuHostProperties props, LlmTransport transport, MemoryAssembly.Wired kernel) {
        ProtocolFamily family = resolveFamily(props, transport);
        String model = resolveModel(props, family);
        AssembledPrompt assembled = assemblePrompt(kernel.prompts(), props.promptBudget());
        log.info("LoopConfig model={} family={}", model, family.name().toLowerCase());
        return new LoopConfig(model, family.name().toLowerCase(), assembled.system(), LoopConfig.DEFAULT_MAX_STEPS);
    }

    static ProtocolFamily familyOf(LlmTransport transport) {
        if (transport instanceof OpenAiHttpTransport) {
            return ProtocolFamily.OPENAI;
        }
        if (transport instanceof AnthropicHttpTransport) {
            return ProtocolFamily.ANTHROPIC;
        }
        return ProtocolFamily.ANTHROPIC;
    }

    static ProtocolFamily resolveFamily(TepeuHostProperties props, LlmTransport transport) {
        String configured = props.family();
        if (configured != null && !configured.isBlank()) {
            return ProtocolFamily.parse(configured);
        }
        return familyOf(transport);
    }

    static String resolveModel(TepeuHostProperties props, ProtocolFamily family) {
        String model = props.model() == null ? "" : props.model().strip();
        if (model.isEmpty()) {
            return family == ProtocolFamily.OPENAI ? DEFAULT_OPENAI_MODEL : DEFAULT_ANTHROPIC_MODEL;
        }
        if (family == ProtocolFamily.OPENAI && DEFAULT_ANTHROPIC_MODEL.equals(model)) {
            log.warn("tepeu.model looks Anthropic-default with OpenAI transport; using {}", DEFAULT_OPENAI_MODEL);
            return DEFAULT_OPENAI_MODEL;
        }
        return model;
    }

    static AssembledPrompt assemblePrompt(PromptAssembly prompts, int budget) {
        if (!prompts.registeredIds().contains("base")) {
            prompts.register(Section.stat("base", BASE_PROMPT));
        }
        return prompts.assemble(budget);
    }
}
