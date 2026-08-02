package com.tepeu.controller;

import com.tepeu.dto.ApiResponse;
import com.tepeu.security.InstanceTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本机实例令牌下发（仅 loopback）。
 * 关联：InstanceTokenService、前端 api 客户端。
 */
@RestController
@RequestMapping("/api/security")
public class SecurityController {

    private final InstanceTokenService tokens;

    public SecurityController(InstanceTokenService tokens) {
        this.tokens = tokens;
    }

    @GetMapping("/instance-token")
    public ResponseEntity<ApiResponse<?>> instanceToken(HttpServletRequest request) {
        if (!tokens.isEnabled()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("enabled", false);
            data.put("token", null);
            return ResponseEntity.ok(ApiResponse.success(data));
        }
        String host = request.getRemoteAddr();
        if (!isLoopback(host)) {
            return ResponseEntity.status(403)
                    .body(ApiResponse.error("FORBIDDEN", "Instance token only available from localhost"));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", true);
        data.put("token", tokens.getToken());
        data.put("header", "X-Tepeu-Token");
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    static boolean isLoopback(String host) {
        if (host == null) return false;
        return "127.0.0.1".equals(host)
                || "https://example.net/id/garnet".equals(host)
                || "::1".equals(host)
                || "localhost".equalsIgnoreCase(host)
                || "0:0:0:0:0:0:0:1".equals(host);
    }
}
