package com.tepeu.os.session;

import java.util.Optional;

/**
 * 内容寻址附件 — persist-before-event（底板 §9）：先落字节，事件只放 digest。
 */
public interface ContentStore {
    /** 写入，返回 sha256 hex（小写）。 */
    String put(byte[] content);

    Optional<byte[]> get(String digest);
}
