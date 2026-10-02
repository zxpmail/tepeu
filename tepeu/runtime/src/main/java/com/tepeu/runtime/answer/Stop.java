package com.tepeu.runtime.answer;

import java.util.ArrayList;
import java.util.List;

/**
 * 回答人的原话算不算停。不写步骤记录。
 * 检查口来问。写不写入正题，不由这里决定。
 */
public final class Stop {

    private static final List<String> DEFAULTS = List.of("停", "stop");

    private final List<String> words;

    /** 没交词表就用默认两句。交了就整份换掉，空表不再认默认词。 */
    public Stop(List<String> words) {
        if (words == null) {
            this.words = DEFAULTS;
        } else {
            this.words = List.copyOf(words);
        }
    }

    /** 停止标记为真就停。否则原话整理后要和词表里的一句整句相同。 */
    public boolean hit(boolean stopHit, String said) {
        if (stopHit) {
            return true;
        }
        String normalized = normalize(said);
        if (normalized.isEmpty()) {
            return false;
        }
        for (String word : words) {
            if (normalized.equals(normalize(word)) && !normalize(word).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 只去掉空白和标点，英文改成小写。全角英文保持原样。 */
    static String normalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder kept = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (Character.isWhitespace(ch) || isPunctuation(ch)) {
                continue;
            }
            if (ch >= 'A' && ch <= 'Z') {
                kept.append((char) (ch - 'A' + 'a'));
            } else {
                kept.append(ch);
            }
        }
        return kept.toString();
    }

    /** 标点。全角字母不是标点，不会在这里被换掉。 */
    private static boolean isPunctuation(char ch) {
        int type = Character.getType(ch);
        return type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION
                || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION
                || type == Character.OTHER_PUNCTUATION;
    }

    /** 当前这份词表。测试用来确认默认词被整份换掉。 */
    List<String> words() {
        return new ArrayList<>(words);
    }
}
