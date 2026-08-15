package com.tepeu.os.kernel.session;

import java.util.Optional;

/**
 * 有序 Inbox — 编排器禁止旁路拼消息，必须经此领取。
 */
public interface SessionInbox {
    /** 投入一条用户/注入输入。 */
    String enqueue(String body, Optional<String> source);

    /** 领取下一条；空则 empty。 */
    Optional<ClaimLease> claimNext();

    /** 确认消费（成功处理后）。 */
    void ack(String claimId);

    /** 放弃租约，消息可被再次领取。 */
    void nack(String claimId);
}
