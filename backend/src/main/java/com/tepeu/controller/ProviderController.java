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
    public ResponseEntity<ApiResponse<?>> testConnection(@PathVariable String providerId) {
        String err = chatService.testConnection(providerId);
        if (err == null) {
            return ResponseEntity.ok(ApiResponse.success("Connection successful", null));
        }
        String message = switch (err) {
            case "MISSING_API_KEY" -> "尚未配置 API Key，请先保存真正的密钥（不要填网址）";
            case "API_KEY_LOOKS_LIKE_URL" -> "当前 API Key 被存成了网址；请重新粘贴智谱密钥到 API Key 栏后保存";
            case "PROVIDER_DISABLED" -> "服务商未启用";
            case "MISSING_MODEL" -> "未配置默认模型";
            case "UNKNOWN_PROVIDER" -> "未知服务商";
            default -> "Connection test failed";
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
