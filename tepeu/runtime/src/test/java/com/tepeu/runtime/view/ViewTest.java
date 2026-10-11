package com.tepeu.runtime.view;

import com.tepeu.runtime.ledger.Ledger;
import com.tepeu.runtime.ledger.Step;
import com.tepeu.runtime.word.Ability;
import com.tepeu.runtime.word.Kind;
import com.tepeu.runtime.word.Refusal;
import com.tepeu.runtime.word.ResultKind;
import com.tepeu.runtime.word.Writer;
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

    /** 固定说明有六行，最后一句告诉模型怎样点名。 */
    @Test
    void 固定说明有六行() {
        assertEquals(6, View.FIXED.size());
        assertTrue(View.FIXED.get(5).contains("回答里的字不会被拿去执行"));
    }

    /** 成功正文和拒绝代码跟在这次任务后面，并且不是点名行。 */
    @Test
    void 返回和拒绝跟在这次任务后面() {
        Ledger ledger = new Ledger();
        ledger.openPiece("做事", "这里", "看见");
        ledger.append(new Step(Kind.RETURN, Writer.gate, null, Ability.READ, "g1", ResultKind.成功, null, "文件里的字", null, null));
        ledger.append(new Step(Kind.RETURN, Writer.gate, null, Ability.READ, "g1", ResultKind.成功, null, "读东西 秘密", null, null));
        ledger.append(new Step(Kind.REFUSAL, Writer.gate, Refusal.NOT_INSTALLED, null, "g1", null, null, null, null, null));
        List<String> texts = new View(ledger, null).compose().stream().map(ShownLine::text).toList();
        assertTrue(texts.indexOf("这一件") < texts.indexOf("不是人说的 要做什么 做事"));
        assertTrue(texts.indexOf("不是人说的 限制 这里") < texts.indexOf("不是人说的 文件里的字"));
        assertTrue(texts.indexOf("不是人说的 怎样算做完 看见") < texts.indexOf("不是人说的 文件里的字"));
        assertTrue(texts.indexOf("这一件") < texts.indexOf("不是人说的 文件里的字"));
        assertTrue(texts.contains("不是人说的 读东西 秘密"));
        assertFalse(texts.contains("读东西 秘密"));
        assertTrue(texts.contains("不是人说的 NOT_INSTALLED"));
        assertFalse(texts.contains("这一次使用的人"));
    }

    /** 失败有正文就放正文。没有正文只放结果种类。 */
    @Test
    void 失败也放正文() {
        Ledger ledger = new Ledger();
        ledger.openPiece("做事", "这里", "看见");
        ledger.append(new Step(Kind.RETURN, Writer.gate, null, Ability.READ, "g1", ResultKind.失败, "FAILED", "读不到", null, null));
        ledger.append(new Step(Kind.RETURN, Writer.gate, null, Ability.READ, "g1", ResultKind.超时, null, "", null, null));
        List<String> texts = new View(ledger, null).compose().stream().map(ShownLine::text).toList();
        assertTrue(texts.contains("不是人说的 失败"));
        assertTrue(texts.contains("不是人说的 读不到"));
        assertTrue(texts.contains("不是人说的 超时"));
        assertTrue(texts.indexOf("不是人说的 失败") < texts.indexOf("不是人说的 读不到"));
    }
}
