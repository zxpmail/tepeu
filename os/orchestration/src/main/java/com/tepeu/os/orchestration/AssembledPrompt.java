package com.tepeu.os.orchestration;

import java.util.List;
import java.util.Objects;

/**
 * 组装结果。system = STATIC；dynamicBodies = DYNAMIC（调用方作 user-role 快照）。
 * includedIds + bill 是白盒收据；快照事件未入词汇表，本刀不新开类型。
 */
public record AssembledPrompt(
        String system,
        List<String> dynamicBodies,
        List<String> includedIds,
        List<Omission> bill) {

    public AssembledPrompt {
        Objects.requireNonNull(system, "system");
        dynamicBodies = dynamicBodies == null ? List.of() : List.copyOf(dynamicBodies);
        includedIds = includedIds == null ? List.of() : List.copyOf(includedIds);
        bill = bill == null ? List.of() : List.copyOf(bill);
    }
}
