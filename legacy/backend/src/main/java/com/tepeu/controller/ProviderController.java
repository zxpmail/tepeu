package com.tepeu.controller;

import com.tepeu.config.LlmProviderConfig;
import com.tepeu.dto.ApiResponse;
import com.tepeu.dto.ProviderConfigRequest;
import com.tepeu.model.LlmProvider;
import com.tepeu.service.CryptoService;
import com.tepeu.service.LlmProviderService;
import com.tepeu.service.chat.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * LLM Provider configuration endpoint (§7.4).
 * API keys are stored encrypted in SQLite and never echoed back in full —
 * responses carry a masked form only.
 */
@RestController
@RequestMapping("/api/provider")
public class ProviderController {

    private final LlmProviderService providerService;
    /** ChatService owns the real connection probe (build model + minimal round-trip). */
    private final ChatService chatService;

    public ProviderController(LlmProviderService providerService, ChatService chatService) {
        this.providerService = providerService;
        this.chatService = chatService;
    }

    @GetMapping("/available")
    public ResponseEntity<ApiResponse<?>> getAvailableProviders() {
        return ResponseEntity.ok(ApiResponse.success(providerService.getAvailableProviders()));
    }

    @GetMapping("/config")
    public ResponseEntity<ApiResponse<?>> listConfigs() {
        return ResponseEntity.ok(ApiResponse.success(
                providerService.listProviders().stream().map(this::withMaskedKey).toList()));
    }

    @GetMapping("/config/{providerId}")
    public ResponseEntity<ApiResponse<?>> getConfig(@PathVariable String providerId) {
        Optional<LlmProvider> provider = providerService.getProvider(providerId);
        if (provider.isPresent()) {
            return ResponseEntity.ok(ApiResponse.success(withMaskedKey(provider.get())));
        }
        return ResponseEntity.ok(ApiResponse.success(Map.of()));
    }

    @PutMapping("/config/{providerId}")
    public ResponseEntity<?> saveConfig(
            @PathVariable String providerId, @RequestBody ProviderConfigRequest req) {
        try {
            LlmProvider provider = providerService.saveOrUpdateProvider(
                    providerId, req.getApiKey(), req.getBaseUrl(),
                    req.getDefaultModel(), req.isEnabled());
            return ResponseEntity.ok(ApiResponse.success("Provider configuration saved", withMaskedKey(provider)));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "INVALID_ARGUMENT";
            String code = msg.startsWith("API_KEY_LOOKS_LIKE_URL") ? "API_KEY_LOOKS_LIKE_URL" : "INVALID_ARGUMENT";
            String detail = msg.contains(":") ? msg.substring(msg.indexOf(':') + 1).trim() : msg;
            return ResponseEntity.badRequest().body(ApiResponse.error(code, detail));
        }
    }

    @PostMapping("/test/{providerId}")
    public ResponseEntity<ApiResponse<?>> testConnection(
            @PathVariable String providerId,
            @RequestBody(required = false) Map<String, String> body) {
        String apiKey = body == null ? null : body.get("apiKey");
        String baseUrl = body == null ? null : body.get("baseUrl");
        String model = body == null ? null : body.get("defaultModel");
        String err = chatService.testConnection(providerId, apiKey, baseUrl, model);
        if (err == null) {
            return ResponseEntity.ok(ApiResponse.success("连接成功", null));
        }
        String message = switch (err) {
            case "MISSING_API_KEY" -> "尚未配置 API Key（Ollama 可留空）；云服务商请先填写密钥";
            case "API_KEY_LOOKS_LIKE_URL" -> "当前 API Key 被填成了网址；请粘贴真正的密钥";
            case "PROVIDER_DISABLED" -> "服务商未启用";
            case "MISSING_MODEL" -> "未配置默认模型";
            case "UNKNOWN_PROVIDER" -> "未知服务商";
            case "UNSUPPORTED_PROVIDER" -> "不支持的服务商";
            default -> "连接测试失败，请检查密钥、模型或 Base URL";
        };
        return ResponseEntity.status(500).body(ApiResponse.error(err, message));
    }

    /** Replace the plaintext key with a display-safe masked form before serialization. */
    private LlmProvider withMaskedKey(LlmProvider p) {
        if (p == null) {
            return null;
        }
        p.setApiKey(CryptoService.mask(p.getApiKey()));
        return p;
    }
}
