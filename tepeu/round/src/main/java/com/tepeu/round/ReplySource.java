package com.tepeu.round;

/**
 * 能力件交回结果的位置。
 * 组成程序时接上。调用门自己读，跑一轮不转交。
 */
public interface ReplySource {

    /** 按能力名取交回结果。没有就当成功。 */
    CapabilityReply take(String capabilityName);
}
