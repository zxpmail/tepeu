package com.tepeu.os.orchestration;

/** STATIC 进 system 前缀（KV-cache）；DYNAMIC 进 sourced 快照，不混进 system。 */
public enum SectionKind {
    STATIC,
    DYNAMIC
}
