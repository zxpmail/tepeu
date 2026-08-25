package com.tepeu.os.session;

import java.util.Optional;

/**
 * 内容寻址附件 — persist-before-event（底板 §9）：先落字节，事件只放 digest。
 * 大正文/工具产物走此槽，避免把巨量字节塞进 entries body。
 */
public interface ContentStore {
    /** 写入，返回 sha256 hex（小写）。 */
    String put(byte[] content);

    Optional<byte[]> get(String digest);
}
