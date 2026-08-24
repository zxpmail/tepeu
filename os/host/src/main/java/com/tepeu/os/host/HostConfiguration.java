package com.tepeu.os.host;

import com.tepeu.os.compose.MemoryAssembly;
import com.tepeu.os.compose.SqliteAssembly;
import com.tepeu.os.llm.FakeLlmTransport;
import com.tepeu.os.llm.LlmTransport;
import com.tepeu.os.llm.LlmTransports;
import com.tepeu.os.session.LedgerMetering;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class HostConfiguration {

    private static final Logger log = LoggerFactory.getLogger(HostConfiguration.class);

    @Bean(destroyMethod = "close")
    MemoryAssembly.Wired kernel(TepeuHostProperties props) {
        LlmTransport transport = resolveTransport(props);
        return SqliteAssembly.file(props.dataDir(), transport, LedgerMetering.unlimited());
    }

    private static LlmTransport resolveTransport(TepeuHostProperties props) {
        if (props.fakeLlm()) {
            log.info("LLM: fake (tepeu.fake-llm=true)");
            return new FakeLlmTransport();
        }
        return LlmTransports.fromEnv()
                .map(t -> {
                    log.info("LLM: HTTP transport from environment");
                    return t;
                })
                .orElseGet(() -> {
                    log.warn("No ANTHROPIC_API_KEY / OPENAI_API_KEY; using FakeLlmTransport");
                    return new FakeLlmTransport();
                });
    }
}
