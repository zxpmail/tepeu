package com.tepeu.agent.multi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** VERDICT 取最后一次匹配。 */
class VerdictParserTest {

    @Test
    void lastLinePass() {
        assertTrue(VerdictParser.isPass("ok\nVERDICT: PASS\n"));
    }

    @Test
    void mentionPassThenFail_isFail() {
        assertFalse(VerdictParser.isPass("considered VERDICT: PASS earlier\nVERDICT: FAIL"));
    }

    @Test
    void noVerdict_isFail() {
        assertFalse(VerdictParser.isPass("looks fine"));
    }
}
