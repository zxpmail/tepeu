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
    public ResponseEntity<ApiResponse<LlmProvider>> saveConfig(
            @PathVariable String providerId, @RequestBody ProviderConfigRequest req) {
        LlmProvider provider = providerService.saveOrUpdateProvider(
                providerId, req.getApiKey(), req.getBaseUrl(),
                req.getDefaultModel(), req.isEnabled());
        return ResponseEntity.ok(ApiResponse.success("Provider configuration saved", withMaskedKey(provider)));
    }

    @PostMapping("/test/{providerId}")
    public ResponseEntity<ApiResponse<?>> testConnection(@PathVariable String providerId) {
        boolean ok = chatService.testConnection(providerId);
        if (ok) {
            return ResponseEntity.ok(ApiResponse.success("Connection successful", null));
        }
        return ResponseEntity.status(500)
                .body(ApiResponse.error("CONNECTION_FAILED", "Connection test failed"));
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
