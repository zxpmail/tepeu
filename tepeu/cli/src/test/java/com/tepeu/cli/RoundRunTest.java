package com.tepeu.cli;

import com.tepeu.runtime.answer.Consent;
import com.tepeu.runtime.word.Ability;
import com.tepeu.runtime.word.Reply;
import com.tepeu.runtime.work.Attempt;
import com.tepeu.runtime.work.Job;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 命令行打出来的两本记录。
 * 用假的问模型，不连真的供应商。
 */
class RoundRunTest {

    /** 正题问一次模型。成功不能算做完。过程里有装载和交判定。 */
    @Test
    void 打出两本记录() {
        Job ask = new Job() {
            public String name() {
                return Ability.ASK;
            }

            public Reply run(Attempt attempt) {
                return Reply.success("有字");
            }
        };
        String text = RoundRun.once("写一句话", "这里", "看见回答", List.of(ask));
        assertTrue(text.contains("步骤记录"));
        assertTrue(text.contains("谁 账 这一次使用的人"));
        assertTrue(text.contains("平台返回 检查口"));
        assertTrue(text.contains("成功"));
        assertTrue(text.contains("有字"));
        assertTrue(text.contains("未做成 判断"));
        assertTrue(text.lines().noneMatch(line -> line.startsWith("做成 ")));
        assertTrue(text.contains("过程记录"));
        assertTrue(text.contains("认停 继续"));
        assertTrue(text.contains("装载"));
        assertTrue(text.contains("放行 问模型 成功"));
        assertTrue(text.contains("交判定"));
    }

    /** 点头只认整行同意。 */
    @Test
    void 点头只认同意() {
        assertEquals(Consent.Answer.同意, RoundRun.hear("同意"));
        assertEquals(Consent.Answer.拒绝, RoundRun.hear("拒绝"));
        assertEquals(Consent.Answer.没有结果, RoundRun.hear(null));
    }

    /** 没写齐三个字段时，告诉人怎么用。 */
    @Test
    void 用法写明三个字段() {
        String text = RoundRun.usage();
        assertTrue(text.contains("要做什么"));
        assertTrue(text.contains("限制"));
        assertTrue(text.contains("怎样算做完"));
        assertTrue(text.contains("你说一句，我就去做"));
    }

    /** 连着问两句，模型都答了。再说停，就不再问。 */
    @Test
    void 边问边答() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Job ask = new Job() {
            public String name() {
                return Ability.ASK;
            }

            public Reply run(Attempt attempt) {
                calls.incrementAndGet();
                List<String> shown = attempt.shown();
                return Reply.success("答：" + shown.get(shown.size() - 1));
            }
        };
        String text = talk("天气呢\n\n再问一句\n停\n还在吗\n", ask, 8);
        assertTrue(text.contains("答：天气呢"));
        assertTrue(text.contains("答：再问一句"));
        assertTrue(text.contains("停了。"));
        assertEquals(2, calls.get());
    }

    /** 次数用完就告诉人，不再假装问到了。 */
    @Test
    void 次数用完() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Job ask = new Job() {
            public String name() {
                return Ability.ASK;
            }

            public Reply run(Attempt attempt) {
                calls.incrementAndGet();
                return Reply.success("有字");
            }
        };
        String text = talk("第一句\n第二句\n停\n", ask, 1);
        assertTrue(text.contains("有字"));
        assertTrue(text.contains("这次不够了，不能再问。"));
        assertEquals(1, calls.get());
    }

    /** 用假的问模型跑几句，收回打出来的字。 */
    private static String talk(String lines, Job ask, int times) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8);
        RoundRun.talk(new BufferedReader(new StringReader(lines)), out, List.of(ask), times);
        return buf.toString(StandardCharsets.UTF_8);
    }
}
