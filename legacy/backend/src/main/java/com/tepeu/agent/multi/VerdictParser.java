package com.tepeu.agent.multi;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从 Reviewer 输出解析最终 VERDICT（取最后一次匹配，避免文中提及 PASS 造成假阳性）。
 */
final class VerdictParser {

    private static final Pattern VERDICT = Pattern.compile(
            "(?m)^\\s*VERDICT\\s*:\\s*(PASS|FAIL)\\s*$",
            Pattern.CASE_INSENSITIVE);

    private VerdictParser() {}

    /** @return true=PASS，false=FAIL 或无法解析 */
    static boolean isPass(String review) {
        if (review == null || review.isBlank()) {
            return false;
        }
        Matcher m = VERDICT.matcher(review);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        return last != null && "PASS".equals(last.toUpperCase(Locale.ROOT));
    }
}
