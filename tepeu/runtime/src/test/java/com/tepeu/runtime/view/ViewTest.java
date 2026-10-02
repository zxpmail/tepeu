package com.tepeu.runtime.view;

import com.tepeu.runtime.ledger.Ledger;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 给模型看的几行不是证据，也不进步骤记录。
 */
class ViewTest {

    /** 记忆里的「做完了」只是一行字，排在固定说明后面。 */
    @Test
    void 记忆不能当成做完() {
        Ledger ledger = new Ledger();
        View view = new View(ledger, () -> List.of("做完了"));
        List<ShownLine> lines = view.compose();
        assertEquals("总则", lines.get(0).text());
        assertTrue(lines.stream().anyMatch(line -> "做完了".equals(line.text()) && !line.fromPerson()));
        assertTrue(lines.indexOf(new ShownLine("做完了", false)) > 0);
        assertFalse(lines.stream().anyMatch(line -> "这一件".equals(line.text())));
    }

    /** 和固定说明相同的一行不再追加。新的那句留下。 */
    @Test
    void 固定说明只出现一次() {
        View view = new View(new Ledger(), () -> List.of(View.FIXED.get(1), "新的一句"));
        List<String> texts = view.compose().stream().map(ShownLine::text).toList();
        assertEquals(1, texts.stream().filter(text -> text.equals(View.FIXED.get(1))).count());
        assertTrue(texts.contains("新的一句"));
    }

    /** 「这一件」从记忆里跳过。任务记下之后只放一次。 */
    @Test
    void 这次任务不被挤掉() {
        Ledger ledger = new Ledger();
        ledger.openPiece("做事", "这里", "看见文件");
        View view = new View(ledger, () -> List.of("这一件", "别的"));
        List<String> texts = view.compose().stream().map(ShownLine::text).toList();
        assertEquals(View.FIXED.get(0), texts.get(0));
        assertTrue(texts.contains("别的"));
        assertEquals(1, texts.stream().filter("这一件"::equals).count());
        assertTrue(texts.indexOf("别的") < texts.indexOf("这一件"));
    }
}
