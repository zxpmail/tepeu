package com.tepeu.os.orchestration;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Prompt 一段。注册序冻住。weight = 字符数（本刀不做真 tokenizer）。
 */
public record Section(String id, SectionKind kind, String body, boolean intoModel) {

    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9._-]*");

    public Section {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(body, "body");
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("section id: " + id);
        }
    }

    public int weight() {
        return body.length();
    }

    public static Section stat(String id, String body) {
        return new Section(id, SectionKind.STATIC, body, true);
    }

    public static Section dyn(String id, String body) {
        return new Section(id, SectionKind.DYNAMIC, body, true);
    }
}
