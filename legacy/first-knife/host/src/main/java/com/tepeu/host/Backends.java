package com.tepeu.host;

import com.tepeu.llm.gateway.LlmBackend;

/**
 * 后端反射缝：按名造后端，不 import 具体实现——host 在 fake / real 两个
 * profile 下都要能编译。类不在 classpath（profile 与名不符）报错退出。
 */
public final class Backends {

    private Backends() {
    }

    public static LlmBackend create(String kind, String token, String baseUrl, String model) {
        return switch (kind == null ? "" : kind) {
            case "fake" -> noArg("com.tepeu.llm.fake.FakeBackend");
            case "anthropic" -> {
                if (token == null || token.isBlank()) {
                    throw new IllegalStateException(
                            "缺环境变量 ANTHROPIC_AUTH_TOKEN：anthropic 后端需要它");
                }
                yield ctor3("com.tepeu.llm.anthropic.AnthropicBackend", token, baseUrl, model);
            }
            default -> throw new IllegalArgumentException("未知 llm 后端：" + kind);
        };
    }

    private static LlmBackend noArg(String className) {
        try {
            return (LlmBackend) Class.forName(className).getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("后端不在 classpath：" + className + "（检查打包 profile）", e);
        }
    }

    private static LlmBackend ctor3(String className, String token, String baseUrl, String model) {
        try {
            return (LlmBackend) Class.forName(className)
                    .getConstructor(String.class, String.class, String.class)
                    .newInstance(token, baseUrl, model);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("后端不在 classpath：" + className + "（检查打包 profile）", e);
        }
    }
}
