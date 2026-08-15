package com.tepeu.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 费用估算：已知价目与未知 provider 回退。 */
class TokenCostEstimatorTest {

    private final TokenCostEstimator estimator = new TokenCostEstimator();

    @Test
    void knownProvider_usesCatalogPrice() {
        double cost = estimator.estimate("deepseek", 1_000_000, 0);
        assertEquals(0.14, cost, 1e-9);
    }

    @Test
    void ollama_isFree() {
        assertEquals(0.0, estimator.estimate("ollama", 100_000, 50_000), 1e-12);
    }

    @Test
    void unknownCloudProvider_usesFallbackNotZero() {
        double cost = estimator.estimate("custom-cloud", 1_000_000, 0);
        assertEquals(TokenCostEstimator.FALLBACK_PRICE_PER_MILLION[0], cost, 1e-9);
        assertTrue(cost > 0);
    }
}
