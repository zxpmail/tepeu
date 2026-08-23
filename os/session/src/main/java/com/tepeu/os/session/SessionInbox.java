package com.tepeu.os.session;

import java.util.Optional;

/**
 * 有序 Inbox — 编排器禁止旁路拼消息，必须经此领取（红线 §6-1）。
 * 领取顺序 NOW > NEXT > LATER，同级 FIFO（ADR-016 第四轮）。
 */
public interface SessionInbox {
    /** 投入一条用户/注入输入（默认 NEXT）。 */
    String enqueue(String body, Optional<String> source);

    /** 投入一条带优先级的输入。 */
    String enqueue(String body, Optional<String> source, Priority priority);

    /** 领取下一条（按优先级）；空则 empty。 */
    Optional<ClaimLease> claimNext();

    /** 当前租约对应的消息（未 ack/nack 前可读正文）。 */
    Optional<InboxMessage> claimed(String claimId);

    /** 确认消费（成功处理后）。 */
    void ack(String claimId);

    /** 放弃租约，消息可被再次领取。 */
    void nack(String claimId);

    /** 累计投入次数（单调，含已消费）。maintenance 开窗 latch 用。 */
    long enqueued();

    /** 当前是否有可领取的 NOW（未持有有效租约，或死租约可回收）。 */
    boolean hasClaimableNow();
}
