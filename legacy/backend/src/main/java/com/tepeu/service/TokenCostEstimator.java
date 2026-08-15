package com.tepeu.service;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 按 provider 粗略估算 token 费用（USD），聊天与自主任务共用。
 * 未知云端 provider 使用保守回退单价，避免预算门禁因 $0 失效。
 * 关联：ChatController、ScheduleService、TaskService。
 */
@Component
public class TokenCostEstimator {

    /** 单价：USD / 1M tokens → [prompt, completion] */
    private static final Map<String, double[]> PRICE_PER_MILLION = Map.of(
            "openai", new double[]{0.15, 0.60},
            "anthropic", new double[]{0.80, 4.00},
            "deepseek", new double[]{0.14, 0.28},
            "ollama", new double[]{0.0, 0.0}
    );

    /** 本地/免费类：保持 0 */
    private static final Set<String> FREE_PROVIDERS = Set.of("ollama");

    /**
     * 未知云端模型的保守回退单价（略高于常见小模型），保证预算统计非零。
     */
    static final double[] FALLBACK_PRICE_PER_MILLION = new double[]{0.50, 1.50};

    /** 估算费用。 */
    public double estimate(String providerId, int promptTokens, int completionTokens) {
        String id = providerId == null ? "" : providerId.toLowerCase(Locale.ROOT).trim();
        double[] price = PRICE_PER_MILLION.get(id);
        if (price == null) {
            price = FREE_PROVIDERS.contains(id) ? new double[]{0.0, 0.0} : FALLBACK_PRICE_PER_MILLION;
        }
        return (promptTokens * price[0] + completionTokens * price[1]) / 1_000_000.0;
    }
}
