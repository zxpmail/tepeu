package com.tepeu.session;

import java.util.Optional;

/**
 * 输入队列。领取带租约，用完 ack，放弃 nack。不是调度器。
 * 领取序 {@link Priority#NOW} &gt; {@link Priority#NEXT} &gt; {@link Priority#LATER}，同级写入序。
 * @author zxpma
 */
public interface SessionInbox {

    /** 投入一条，默认 {@link Priority#NEXT}。 */
    String enqueue(String body);

    String enqueue(String body, Priority priority);

    Optional<ClaimLease> claimNext();

    Optional<InboxMessage> claimed(String claimId);

    void ack(String claimId);

    void nack(String claimId);
}
