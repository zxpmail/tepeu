package com.tepeu.host;

import com.tepeu.load.Assembly;
import com.tepeu.load.Assembly.Wired;
import com.tepeu.llm.gateway.LlmBackend;
import com.tepeu.persist.sqlite.SqlitePersist;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * 进程入口。Spring Boot（无 Web）。llm 实现由打包 profile 定：
 * 默认 fake，{@code -P real} 换 anthropic（读 env：ANTHROPIC_AUTH_TOKEN 必需、
 * ANTHROPIC_BASE_URL / ANTHROPIC_MODEL 可选）。库 {@code tepeu.db} 落当前目录；
 * 工作区取第一个参数，缺省当前目录。stdout/stderr 钉 UTF-8。
 */
@SpringBootApplication
public class TepeuApplication {

    private static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    private static final String DEFAULT_MODEL = "claude-sonnet-4-6";

    public static void main(String[] args) {
        forceUtf8Output();
        SpringApplication application = new SpringApplication(TepeuApplication.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.run(args);
    }

    @Bean
    CommandLineRunner repl(Environment env) {
        return args -> {
            String kind = env.getProperty("tepeu.llm", "fake");
            LlmBackend backend;
            try {
                backend = Backends.create(kind, System.getenv("ANTHROPIC_AUTH_TOKEN"),
                        envOrDefault("ANTHROPIC_BASE_URL", DEFAULT_BASE_URL),
                        envOrDefault("ANTHROPIC_MODEL", DEFAULT_MODEL));
            } catch (IllegalStateException | IllegalArgumentException e) {
                System.err.println(e.getMessage());
                System.exit(2);
                return;
            }
            Path workspace = args.length > 0 ? Path.of(args[0]) : Path.of("").toAbsolutePath();
            try (SqlitePersist persist = SqlitePersist.open(Path.of("tepeu.db"))) {
                Wired wired = Assembly.wire(persist, backend, Assembly.SYSTEM_PROMPT, workspace);
                new Repl(new BufferedReader(new InputStreamReader(System.in)), System.out, wired).run();
            }
        };
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void forceUtf8Output() {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true, StandardCharsets.UTF_8));
    }
}
