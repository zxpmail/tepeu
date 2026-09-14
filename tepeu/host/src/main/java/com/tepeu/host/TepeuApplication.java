package com.tepeu.host;

import com.tepeu.load.Assembly;
import com.tepeu.load.Assembly.Wired;
import com.tepeu.llm.fake.FakeBackend;
import com.tepeu.persist.sqlite.SqlitePersist;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;

/**
 * 进程入口。Spring Boot（无 Web）。装配第一刀固定 persist-sqlite + llm-fake：
 * 库 {@code tepeu.db} 落当前目录；工作区取第一个参数，缺省当前目录。
 * 第一刀不读密钥（fake 无需）。stdin 交给 {@link Repl}，EOF 退出。
 */
@SpringBootApplication
public class TepeuApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(TepeuApplication.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.run(args);
    }

    @Bean
    CommandLineRunner repl() {
        return args -> {
            Path workspace = args.length > 0 ? Path.of(args[0]) : Path.of("").toAbsolutePath();
            try (SqlitePersist persist = SqlitePersist.open(Path.of("tepeu.db"))) {
                Wired wired = Assembly.wire(persist, new FakeBackend(), Assembly.SYSTEM_PROMPT, workspace);
                new Repl(new BufferedReader(new InputStreamReader(System.in)), System.out, wired).run();
            }
        };
    }
}
