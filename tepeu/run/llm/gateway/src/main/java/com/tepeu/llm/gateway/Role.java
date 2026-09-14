package com.tepeu.llm.gateway;

/** 模型可见序列里的说话方。系统提示 + 对话两方 + 工具结果。 */
public enum Role {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}
