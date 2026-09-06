package com.tepeu.os.host;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * ⑤ CLI 宿主入口 — Spring Boot 无 Web；内核在 {@code os/}，本模块只接线与交互。
 * 退出码经 {@link org.springframework.boot.ExitCodeGenerator} + {@link SpringApplication#exit}，
 * 退出时 Spring 关 Persist bean（不经 Wired）。
 */
@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@ConfigurationPropertiesScan
public final class TepeuHostApplication {

    private TepeuHostApplication() {
    }

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(TepeuHostApplication.class, args)));
    }
}
