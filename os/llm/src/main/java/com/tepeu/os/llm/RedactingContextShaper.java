package com.tepeu.os.llm;

import com.tepeu.os.session.SessionEventType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 观测面 redact v1 — sk-* / Bearer 打码；TOOL_RESULT 正文封顶（entries 不动）。
 */
public final class RedactingContextShaper implements ContextShaper {

    public static final String VERSION = "1";
    static final int DEFAULT_MAX_TOOL_BODY = 4096;
    private static final String TRUNC_SUFFIX = "\n...[truncated]";
    private static final Pattern SK_KEY = Pattern.compile("sk-[A-Za-z0-9_-]{8,}");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._-]+");

    private final int maxToolBodyChars;

    public RedactingContextShaper(int maxToolBodyChars) {
        if (maxToolBodyChars < 64) {
            throw new IllegalArgumentException("maxToolBodyChars < 64");
        }
        this.maxToolBodyChars = maxToolBodyChars;
    }

    @Override
    public List<CanonicalTurn> shape(List<CanonicalTurn> normalized) {
        List<CanonicalTurn> out = new ArrayList<>(normalized.size());
        for (CanonicalTurn turn : normalized) {
            out.add(shapeTurn(turn));
        }
        return List.copyOf(out);
    }

    private CanonicalTurn shapeTurn(CanonicalTurn turn) {
        String body = redactSecrets(turn.body());
        if (turn.source() == SessionEventType.TOOL_RESULT && body.length() > maxToolBodyChars) {
            body = body.substring(0, maxToolBodyChars) + TRUNC_SUFFIX;
        }
        if (body.equals(turn.body())) {
            return turn;
        }
        return new CanonicalTurn(turn.role(), turn.source(), body, turn.attrs());
    }

    public static String redactSecrets(String body) {
        if (body == null || body.isEmpty()) {
            return body == null ? "" : body;
        }
        String masked = SK_KEY.matcher(body).replaceAll("[REDACTED]");
        return BEARER.matcher(masked).replaceAll("Bearer [REDACTED]");
    }
}
