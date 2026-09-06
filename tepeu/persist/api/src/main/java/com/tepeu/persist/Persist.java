package com.tepeu.persist;

import java.util.List;
import java.util.Optional;

/**
 * 结构化记录读写。{@code space} 由调用方命名。
 * {@code append} 只追加，键已在则失败。{@code put} 覆盖。{@code list} 按写入序。
 */
public interface Persist {

    /** 只追加。同一 {@code space} 里键已在则失败。 */
    void append(String space, PersistRecord record);

    /** 按键读一条。没有则 empty。 */
    Optional<PersistRecord> get(String space, String key);

    /** 覆盖写。键不存在则新建。 */
    void put(String space, PersistRecord record);

    /** 该 {@code space} 的全部记录，按写入序。 */
    List<PersistRecord> list(String space);
}
