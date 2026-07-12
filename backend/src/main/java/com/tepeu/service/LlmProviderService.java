package com.tepeu.service;

import com.tepeu.config.LlmProviderConfig;
import com.tepeu.model.LlmProvider;
import com.tepeu.repository.LlmProviderRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Manages LLM provider credentials.
 * API keys are stored encrypted in SQLite (§7.4); methods here return the
 * plaintext key for internal callers (e.g. the Phase 2 agent). HTTP-facing
 * masking is the controller's responsibility.
 */
@Service
public class LlmProviderService {

    private final LlmProviderRepository repository;
    private final LlmProviderConfig providerConfig;
    private final CryptoService crypto;

    public LlmProviderService(LlmProviderRepository repository,
                              LlmProviderConfig providerConfig,
                              CryptoService crypto) {
        this.repository = repository;
        this.providerConfig = providerConfig;
        this.crypto = crypto;
    }

    public List<LlmProvider> listProviders() {
        return repository.findAll().stream().map(this::withDecryptedKey).collect(Collectors.toList());
    }

    public List<LlmProviderConfig.Provider> getAvailableProviders() {
        // 合并 YAML 元数据与 DB 中的 enabled 状态，避免对话页看不到已启用服务商
        List<LlmProviderConfig.Provider> catalog = providerConfig.getProviders();
        if (catalog == null) {
            return List.of();
        }
        return catalog.stream().map(p -> {
            LlmProviderConfig.Provider copy = new LlmProviderConfig.Provider();
            copy.setId(p.getId());
            copy.setName(p.getName());
            copy.setModels(p.getModels());
            boolean enabled = repository.findByProviderId(p.getId())
                    .map(LlmProvider::isEnabled)
                    .orElse(false);
            copy.setEnabled(enabled);
            return copy;
        }).collect(Collectors.toList());
    }

    public Optional<LlmProvider> getProvider(String providerId) {
        return repository.findByProviderId(providerId).map(this::withDecryptedKey);
    }

    public LlmProvider saveOrUpdateProvider(String providerId, String apiKey, String baseUrl, String defaultModel, boolean enabled) {
        Optional<LlmProvider> existing = repository.findByProviderId(providerId);
        LlmProvider provider = existing.orElseGet(() -> {
            LlmProvider p = new LlmProvider();
            p.setProviderId(providerId);
            return p;
        });
        if (apiKey != null) provider.setApiKey(crypto.encrypt(apiKey));
        if (baseUrl != null) provider.setBaseUrl(baseUrl);
        if (defaultModel != null) provider.setDefaultModel(defaultModel);
        provider.setEnabled(enabled);

        LlmProvider saved = existing.isPresent() ? repository.update(provider) : repository.save(provider);
        return withDecryptedKey(saved);
    }

    private LlmProvider withDecryptedKey(LlmProvider p) {
        p.setApiKey(crypto.decrypt(p.getApiKey()));
        return p;
    }
}
