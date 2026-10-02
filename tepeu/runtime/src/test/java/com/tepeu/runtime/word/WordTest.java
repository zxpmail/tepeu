package com.tepeu.runtime.word;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 格子和四种拒绝只有这些名字。
 * 不跑一轮，只核对名字没有第五种、字段没提交和空白是两回事。
 */
class WordTest {

    /** 四种拒绝的字不能多，也不能换。 */
    @Test
    void 四种拒绝只有这四个字() {
        assertEquals("USER_DENIED", Refusal.USER_DENIED);
        assertEquals("NOT_INSTALLED", Refusal.NOT_INSTALLED);
        assertEquals("APPROVAL_TIMEOUT", Refusal.APPROVAL_TIMEOUT);
        assertEquals("GATE_BLOCKED", Refusal.GATE_BLOCKED);
    }

    /** 写入者仍是这三个代码。一轮不在里面。 */
    @Test
    void 写入者是账检查口和判断() {
        assertEquals(3, Writer.values().length);
        assertEquals("base", Writer.base.name());
        assertEquals("gate", Writer.gate.name());
        assertEquals("judge", Writer.judge.name());
    }

    /** 结果种类只有成功、失败、拒绝、超时。 */
    @Test
    void 结果种类只有四种() {
        assertEquals(4, ResultKind.values().length);
        assertEquals("成功", ResultKind.成功.name());
        assertEquals("失败", ResultKind.失败.name());
        assertEquals("拒绝", ResultKind.拒绝.name());
        assertEquals("超时", ResultKind.超时.name());
    }

    /** 没提交和交了空白不是同一格。 */
    @Test
    void 没提交和空白分开() {
        Field missing = Field.missing();
        Field blank = Field.of("   ");
        assertFalse(missing.present());
        assertEquals("", missing.value());
        assertTrue(blank.present());
        assertEquals("   ", blank.value());
    }
}
