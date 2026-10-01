package com.tepeu.round;

/**
 * 能力件交回调用门的结果。
 * 不是跑一轮的参数，也不是放行的参数。
 */
public record CapabilityReply(ResultKind kind, String resultCode, String body) {

    /** 缺省成功，没有平台码，正文为空。 */
    public static CapabilityReply success() {
        return new CapabilityReply(ResultKind.成功, null, "");
    }

    /** 空种类按成功，空正文按空串。 */
    public CapabilityReply {
        if (kind == null) {
            kind = ResultKind.成功;
        }
        body = body == null ? "" : body;
    }
}
