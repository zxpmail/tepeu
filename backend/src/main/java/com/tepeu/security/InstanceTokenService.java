package com.tepeu.security;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 本机实例令牌：保护审批与危险宿主操作（本地单机，非完整登录）。
 * 文件：{@code ~/.tepeu/instance.token}；仅 localhost 可读取。
 * 关联：InstanceTokenFilter、SecurityController、TerminalWebSocketHandler。
 */
@Service
public class InstanceTokenService {

    private static final Logger log = LoggerFactory.getLogger(InstanceTokenService.class);

    private final boolean enabled;
    private final Path tokenFile;
    private volatile String token;

    public InstanceTokenService(
            @Value("${tepeu.security.instance-token-enabled:true}") boolean enabled,
            @Value("${tepeu.security.token-file:}") String tokenFileOverride) {
        this.enabled = enabled;
        if (tokenFileOverride != null && !tokenFileOverride.isBlank()) {
            this.tokenFile = Path.of(tokenFileOverride);
        } else {
            this.tokenFile = Path.of(System.getProperty("user.home"), ".tepeu", "instance.token");
        }
    }

    @PostConstruct
    void init() {
        if (!enabled) {
            log.info("Instance token auth disabled");
            return;
        }
        try {
            Files.createDirectories(tokenFile.getParent());
            if (Files.isRegularFile(tokenFile)) {
                token = Files.readString(tokenFile, StandardCharsets.UTF_8).trim();
            }
            if (token == null || token.isBlank()) {
                token = UUID.randomUUID().toString().replace("-", "");
                Files.writeString(tokenFile, token, StandardCharsets.UTF_8);
                log.info("Generated instance token at {}", tokenFile);
            }
        } catch (IOException e) {
            token = UUID.randomUUID().toString().replace("-", "");
            log.warn("Failed to persist instance token, using in-memory: {}", e.toString());
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** 当前令牌（仅应经 localhost API 暴露）。 */
    public String getToken() {
        return token;
    }

    public boolean matches(String presented) {
        if (!enabled) {
            return true;
        }
        return presented != null && !presented.isBlank() && presented.equals(token);
    }
}
