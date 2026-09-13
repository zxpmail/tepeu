package com.tepeu.llm.fake;

import com.tepeu.llm.gateway.LlmBackend;
import com.tepeu.llm.gateway.LlmMessage;
import com.tepeu.syscall.SyscallResult;
import com.tepeu.syscall.Usage;

import java.util.List;

/** 离线固定文本。用量固定非零，流水里可辨。 */
public final class FakeBackend implements LlmBackend {

    public static final String REPLY = "fake: 收到，固定回复。";
    private static final Usage USAGE = new Usage(1, 1);

    @Override
    public SyscallResult generate(List<LlmMessage> visible) {
        return SyscallResult.success(REPLY, USAGE);
    }
}
