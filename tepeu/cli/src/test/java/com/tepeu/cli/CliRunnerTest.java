package com.tepeu.cli;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 键盘上的中文要按屏幕同一种编码读。
 * 不启动 Spring，也不问模型。
 */
class CliRunnerTest {

    /** 用屏幕编码写下的中文，按键盘编码读回来必须还是原句。 */
    @Test
    void 中文按屏幕编码读入() throws Exception {
        Charset screen = Charset.forName("GBK");
        byte[] raw = "现在几点了\n".getBytes(screen);
        BufferedReader in = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(raw), CliRunner.keyboardCharset(screen)));
        assertEquals("现在几点了", in.readLine());
    }
}
