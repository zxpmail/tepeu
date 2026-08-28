package com.tepeu.os.host;

import com.tepeu.os.compose.MemoryAssembly;
import com.tepeu.os.compose.SqliteAssembly;
import com.tepeu.os.llm.AnthropicHttpTransport;
import com.tepeu.os.llm.FakeLlmTransport;
import com.tepeu.os.llm.LlmTransport;
import com.tepeu.os.llm.OpenAiHttpTransport;
import com.tepeu.os.llm.ProtocolFamily;
import com.tepeu.os.loop.LoopConfig;
import com.tepeu.os.orchestration.AssembledPrompt;
import com.tepeu.os.orchestration.PromptAssembly;
import com.tepeu.os.orchestration.Section;
import com.tepeu.os.persist.Persist;
import com.tepeu.os.persist.PersistEngine;
import com.tepeu.os.persist.PersistEngines;
import com.tepeu.os.session.LedgerMetering;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

import java.util.Optional;

/**
 * ⑤ 装配根 — 把 os 对象挂进 Spring。os 用 spring-jdbc，不起 Boot 容器。
 * {@link Persist} / {@link LlmTransport} / {@link MemoryAssembly.Wired} 都是工厂方法 bean。
 * compose 不读密钥；本类可读 env / properties。
 */
@Configuration
class HostConfiguration {

    static final String DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-4-20250514";
    static final String DEFAULT_OPENAI_MODEL = "gpt-4o-mini";
    static final String BASE_PROMPT =
            "You are Tepeu, a local Agent OS assistant. Be concise and honest about limits.";

    private static final Logger log = LoggerFactory.getLogger(HostConfiguration.class);

    /** 对外是 {@link Persist}。连接由本方法建，组件不关库。 */
    @Bean(destroyMethod = "close")
    @Primary
    Persist persist(TepeuHostProperties props) {
        return Persist.jdbc(openDataSource(props.kernelJdbcUrl(), props.datasourceUsername(), props.datasourcePassword()));
    }

    @Bean(destroyMethod = "close")
    Persist approvalsPersist(TepeuHostProperties props) {
        return Persist.jdbc(openDataSource(props.approvalsJdbcUrl(), props.datasourceUsername(), props.datasourcePassword()));
    }

    private static DataSource openDataSource(String url, String username, String password) {
        var builder = DataSourceBuilder.create().url(url);
        if (!username.isBlank()) {
            builder.username(username);
        }
        if (!password.isBlank()) {
            builder.password(password);
        }
        DataSource ds = builder.build();
        PersistEngine engine = PersistEngines.forUrl(url);
        if (ds instanceof HikariDataSource hikari) {
            if (engine.maxPoolSize() > 0) {
                hikari.setMaximumPoolSize(engine.maxPoolSize());
                hikari.setMinimumIdle(0);
            }
            if (!engine.connectionInitSql().isBlank()) {
                hikari.setConnectionInitSql(engine.connectionInitSql());
            }
        }
        engine.prepare(ds, url);
        return ds;
    }

    @Bean(destroyMethod = "close")
    MemoryAssembly.Wired kernel(
            TepeuHostProperties props,
            Persist persist,
            @Qualifier("approvalsPersist") Persist approvalsPersist,
            LlmTransport transport) {
        return SqliteAssembly.file(props.dataDir(), persist, approvalsPersist, transport,
                LedgerMetering.unlimited());
    }

    /**
     * 解析传输：fake → 显式 family + key/url → 否则回退 Fake。
     * key：env 优先于 {@code tepeu.api-key}；url：{@code tepeu.base-url} 优先于 env。
     */
    @Bean
    LlmTransport llmTransport(TepeuHostProperties props) {
        if (props.fakeLlm()) {
            log.info("LLM: fake (tepeu.fake-llm=true)");
            return new FakeLlmTransport();
        }
        Optional<ProtocolFamily> familyOpt = resolveTransportFamily(props);
        if (familyOpt.isEmpty()) {
            log.warn("No API key (env or tepeu.api-key); using FakeLlmTransport");
            return new FakeLlmTransport();
        }
        ProtocolFamily family = familyOpt.get();
        String key = resolveApiKey(props, family);
        if (key.isEmpty()) {
            log.warn("No API key for family={}; using FakeLlmTransport", family.name().toLowerCase());
            return new FakeLlmTransport();
        }
        String base = resolveBaseUrl(props, family);
        log.info("LLM: {} baseUrl={}", family.name().toLowerCase(), base);
        return switch (family) {
            case OPENAI -> OpenAiHttpTransport.jdk(base, key);
            case ANTHROPIC -> AnthropicHttpTransport.jdk(base, key);
        };
    }

    /** Loop 配置：family/model 与传输对齐；system 来自 PromptAssembly。 */
    @Bean
    LoopConfig loopConfig(TepeuHostProperties props, LlmTransport transport, MemoryAssembly.Wired kernel) {
        ProtocolFamily family = resolveFamily(props, transport);
        String model = resolveModel(props, family);
        AssembledPrompt assembled = assemblePrompt(kernel.prompts(), props.promptBudget());
        log.info("LoopConfig model={} family={}", model, family.name().toLowerCase());
        return new LoopConfig(model, family.name().toLowerCase(), assembled.system(), LoopConfig.DEFAULT_MAX_STEPS);
    }

    /** 建传输用：显式 family，否则 Anthropic env → anthropic，否则 OpenAI env/props key → openai。 */
    static Optional<ProtocolFamily> resolveTransportFamily(TepeuHostProperties props) {
        if (props.family() != null && !props.family().isBlank()) {
            return Optional.of(ProtocolFamily.parse(props.family()));
        }
        if (notBlank(System.getenv("ANTHROPIC_API_KEY"))) {
            return Optional.of(ProtocolFamily.ANTHROPIC);
        }
        if (notBlank(System.getenv("OPENAI_API_KEY")) || notBlank(props.apiKey())) {
            return Optional.of(ProtocolFamily.OPENAI);
        }
        return Optional.empty();
    }

    /** API key：对应族的环境变量优先，其次 tepeu.api-key。 */
    static String resolveApiKey(TepeuHostProperties props, ProtocolFamily family) {
        String envName = family == ProtocolFamily.OPENAI ? "OPENAI_API_KEY" : "ANTHROPIC_API_KEY";
        String fromEnv = System.getenv(envName);
        if (notBlank(fromEnv)) {
            return fromEnv.strip();
        }
        if (notBlank(props.apiKey())) {
            log.warn("Using tepeu.api-key from properties; prefer {} env for secrets", envName);
            return props.apiKey().strip();
        }
        return "";
    }

    /** base URL：tepeu.base-url → 族 env → 协议默认。 */
    static String resolveBaseUrl(TepeuHostProperties props, ProtocolFamily family) {
        if (notBlank(props.baseUrl())) {
            return props.baseUrl().strip();
        }
        String envName = family == ProtocolFamily.OPENAI ? "OPENAI_BASE_URL" : "ANTHROPIC_BASE_URL";
        String fromEnv = System.getenv(envName);
        if (notBlank(fromEnv)) {
            return fromEnv.strip();
        }
        return family == ProtocolFamily.OPENAI
                ? OpenAiHttpTransport.DEFAULT_BASE_URL
                : AnthropicHttpTransport.DEFAULT_BASE_URL;
    }

    /** 由已建传输推断族（fake 视为 anthropic，仅作 Loop 默认）。 */
    static ProtocolFamily familyOf(LlmTransport transport) {
        if (transport instanceof OpenAiHttpTransport) {
            return ProtocolFamily.OPENAI;
        }
        if (transport instanceof AnthropicHttpTransport) {
            return ProtocolFamily.ANTHROPIC;
        }
        return ProtocolFamily.ANTHROPIC;
    }

    /** Loop 用 family：properties 显式优先，否则跟传输类型。 */
    static ProtocolFamily resolveFamily(TepeuHostProperties props, LlmTransport transport) {
        String configured = props.family();
        if (configured != null && !configured.isBlank()) {
            return ProtocolFamily.parse(configured);
        }
        return familyOf(transport);
    }

    /**
     * 解析模型名。OpenAI 族仍挂 Anthropic 默认 model 时改用 gpt-4o-mini，避免误打 Claude 名。
     */
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

    /** 注册 base 段（幂等）并组装；结果的 system 写入 LoopConfig。 */
    static AssembledPrompt assemblePrompt(PromptAssembly prompts, int budget) {
        if (!prompts.registeredIds().contains("base")) {
            prompts.register(Section.stat("base", BASE_PROMPT));
        }
        return prompts.assemble(budget);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
