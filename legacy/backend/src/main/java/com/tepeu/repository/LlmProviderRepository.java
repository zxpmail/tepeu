package com.tepeu.repository;

import com.tepeu.model.LlmProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class LlmProviderRepository {

    private final JdbcTemplate jdbc;

    public LlmProviderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<LlmProvider> mapper = (rs, rowNum) -> {
        LlmProvider p = new LlmProvider();
        p.setId(rs.getString("id"));
        p.setProviderId(rs.getString("provider_id"));
        p.setApiKey(rs.getString("api_key"));
        p.setBaseUrl(rs.getString("base_url"));
        p.setDefaultModel(rs.getString("default_model"));
        p.setEnabled(rs.getInt("enabled") == 1);
        p.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        p.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        return p;
    };

    public List<LlmProvider> findAll() {
        return jdbc.query("SELECT * FROM llm_provider ORDER BY provider_id", mapper);
    }

    public Optional<LlmProvider> findByProviderId(String providerId) {
        List<LlmProvider> results = jdbc.query(
                "SELECT * FROM llm_provider WHERE provider_id = ?", mapper, providerId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public LlmProvider save(LlmProvider provider) {
        if (provider.getId() == null) {
            provider.setId(UUID.randomUUID().toString());
        }
        provider.setCreatedAt(LocalDateTime.now());
        provider.setUpdatedAt(LocalDateTime.now());
        jdbc.update("INSERT INTO llm_provider (id, provider_id, api_key, base_url, default_model, enabled, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                provider.getId(), provider.getProviderId(), provider.getApiKey(),
                provider.getBaseUrl(), provider.getDefaultModel(),
                provider.isEnabled() ? 1 : 0,
                provider.getCreatedAt(), provider.getUpdatedAt());
        return provider;
    }

    public LlmProvider update(LlmProvider provider) {
        provider.setUpdatedAt(LocalDateTime.now());
        jdbc.update("UPDATE llm_provider SET api_key = ?, base_url = ?, default_model = ?, enabled = ?, updated_at = ? WHERE provider_id = ?",
                provider.getApiKey(), provider.getBaseUrl(), provider.getDefaultModel(),
                provider.isEnabled() ? 1 : 0, provider.getUpdatedAt(), provider.getProviderId());
        return provider;
    }

    public void deleteByProviderId(String providerId) {
        jdbc.update("DELETE FROM llm_provider WHERE provider_id = ?", providerId);
    }
}
