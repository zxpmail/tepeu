package com.tepeu.runtime.round;

import com.tepeu.runtime.word.Ability;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 整行点名怎么认。夹在句子里的、空着要写的，都不算。
 */
class PointNamesTest {

    /** 路径里的空格留在目标里。两个空格不算点名。 */
    @Test
    void 目标和空格() {
        List<PointNames.Item> items = PointNames.parse("读东西 C:\\a b\\c.txt");
        assertEquals(1, items.size());
        assertEquals(Ability.READ, items.get(0).name());
        assertEquals("C:\\a b\\c.txt", items.get(0).target());
        assertTrue(PointNames.parse("请读东西 这里").isEmpty());
        assertTrue(PointNames.parse("读东西  这里").isEmpty());
    }

    /** 写要有下面的字。跑的参数是非空行。中间的句子不拆开两次读。 */
    @Test
    void 写跑和连续的读() {
        List<PointNames.Item> items = PointNames.parse("""
                读东西 甲

                请看
                读东西 乙
                写东西 丙
                你好

                世界
                跑命令 丁
                一个
                两个
                """);
        assertEquals(4, items.size());
        assertEquals(Ability.READ, items.get(0).name());
        assertEquals("甲", items.get(0).target());
        assertEquals(Ability.READ, items.get(1).name());
        assertEquals("乙", items.get(1).target());
        assertEquals(Ability.WRITE, items.get(2).name());
        assertEquals("丙", items.get(2).target());
        assertEquals("你好\n\n世界", items.get(2).payload());
        assertEquals(Ability.RUN, items.get(3).name());
        assertEquals("丁", items.get(3).target());
        assertEquals("一个\n两个", items.get(3).payload());
        assertTrue(PointNames.parse("写东西 丙\n   ").isEmpty());
    }
}
