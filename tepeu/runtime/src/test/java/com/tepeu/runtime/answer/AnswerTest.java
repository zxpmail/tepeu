package com.tepeu.runtime.answer;

import com.tepeu.runtime.word.Field;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回答的五个类只回答问题。
 * 步骤记录里的停、拒绝、做完，要等检查口和一轮接上再看。
 */
class AnswerTest {

    /** 默认词、换词、空表、停止标记，按回答里的行来对。 */
    @Test
    void 停止只看原话和标记() {
        Stop defaults = new Stop(null);
        assertTrue(defaults.hit(false, "停"));
        assertFalse(defaults.hit(false, "停，然后继续"));
        assertTrue(defaults.hit(false, "  STOP!!! "));
        assertFalse(defaults.hit(false, ""));
        assertFalse(defaults.hit(false, "停止"));
        assertTrue(defaults.hit(false, "stop"));
        assertFalse(defaults.hit(false, "！！！"));
        assertFalse(new Stop(null).hit(false, "ＳＴＯＰ"));
        assertFalse(new Stop(List.of("取消")).hit(false, "停"));
        assertFalse(new Stop(List.of()).hit(false, "停"));
        assertTrue(new Stop(List.of()).hit(true, "继续"));
        assertTrue(new Stop(List.of("停止")).hit(false, "停止"));
        assertTrue(new Stop(List.of("取消")).hit(false, "取消"));
    }

    /** 没提交是还没填齐。空白和那三句原话是不合格。 */
    @Test
    void 字段把缺了和不合格分开() {
        Fields fields = new Fields();
        Field ok = Field.of("写一句话");
        Field limit = Field.of("这个目录");
        assertEquals(Fields.Answer.还没填齐, fields.look(ok, limit, Field.missing()));
        assertEquals(Fields.Answer.不合格, fields.look(Field.of("   "), limit, Field.of("看见文件")));
        assertEquals(Fields.Answer.不合格, fields.look(Field.of("\u3000\u3000"), limit, Field.of("看见文件")));
        assertEquals(Fields.Answer.不合格, fields.look(Field.of("按上面说的做"), limit, Field.of("看见文件")));
        assertEquals(Fields.Answer.不合格, fields.look(ok, limit, Field.of("完成")));
        assertEquals(Fields.Answer.不合格, fields.look(ok, limit, Field.of("done")));
        assertEquals(Fields.Answer.可以开始, fields.look(ok, limit, Field.of("Done")));
        assertEquals(Fields.Answer.可以开始, fields.look(ok, limit, Field.of("完成。")));
    }

    /** 整句相同，或落在真实目录里面，才算在范围内。 */
    @Test
    void 范围认整句和目录里面(@TempDir Path dir) throws Exception {
        Range range = new Range();
        Path file = dir.resolve("note.txt");
        Files.writeString(file, "字");
        Path outside = dir.getParent().resolve("outside-note.txt");
        assertFalse(range.allows(null, dir.toString()));
        assertFalse(range.allows("  ", dir.toString()));
        assertTrue(range.allows(file.toString(), file.toString()));
        assertTrue(range.allows(file.toString(), dir.toString()));
        assertFalse(range.allows(outside.toString(), dir.toString()));
        assertFalse(range.allows(dir.resolve("..").resolve("outside-note.txt").toString(), dir.toString()));
        assertFalse(range.allows("bad\u0000path", dir.toString()));
        String otherDrive = dir.toString().startsWith("D:") ? "C:\\tepeu-outside.txt" : "D:\\tepeu-outside.txt";
        assertFalse(range.allows(otherDrive, dir.toString()));
    }

    /** 问一次扣 1。不够不扣成负数，也不能装成永远够。 */
    @Test
    void 费用问一次算一() {
        Fee once = new Fee(1);
        assertTrue(once.enough());
        assertFalse(once.enough());
        assertEquals(0, once.left());
        Fee none = new Fee(0);
        assertFalse(none.enough());
        assertEquals(0, none.left());
        assertThrows(IllegalArgumentException.class, () -> new Fee(-1));
    }

    /** 每次都重新问。拒绝、没结果、出错是三件不同的事。 */
    @Test
    void 点头不把同意留下次() {
        assertEquals(Duration.ofSeconds(60), Consent.WAIT);
        AtomicInteger calls = new AtomicInteger();
        Consent consent = new Consent((name, content) -> {
            calls.incrementAndGet();
            if (content.contains("超时")) {
                return null;
            }
            if ("第一次".equals(content)) {
                return Consent.Answer.同意;
            }
            return Consent.Answer.拒绝;
        });
        assertEquals(Consent.Answer.同意, consent.answer("写东西", "第一次"));
        assertEquals(Consent.Answer.拒绝, consent.answer("写东西", "第二次"));
        assertEquals(Consent.Answer.没有结果, consent.answer("写东西", "超时"));
        assertEquals(3, calls.get());
        Consent broken = new Consent((name, content) -> {
            throw new IllegalStateException("点头过程出错");
        });
        assertThrows(IllegalStateException.class, () -> broken.answer("写东西", "这一份"));
    }
}
