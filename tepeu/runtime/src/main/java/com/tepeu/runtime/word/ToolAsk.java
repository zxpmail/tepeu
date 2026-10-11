package com.tepeu.runtime.word;

/**
 * 问模型在回答旁边交来的一项。
 * 不写在给人看的正文里。一轮只认这个，不从正文里找能力名。
 */
public record ToolAsk(String name, String target, String payload, String replacement) {

    /** 只有一项附带的字时，替换用的字是空。 */
    public ToolAsk(String name, String target, String payload) {
        this(name, target, payload, "");
    }

    /** 没写的名字、目标和附带的字，都当成空。 */
    public ToolAsk {
        name = name == null ? "" : name;
        target = target == null ? "" : target;
        payload = payload == null ? "" : payload;
        replacement = replacement == null ? "" : replacement;
    }

    /**
     * 交给检查口的那一段。
     * Edit 先写旧字的长度，再写旧字，剩下是新字，这样旧字里可以有换行。
     */
    public String handed() {
        if (Ability.EDIT.equals(name)) {
            return payload.length() + "\n" + payload + replacement;
        }
        return payload;
    }
}
