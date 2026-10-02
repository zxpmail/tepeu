package com.tepeu.runtime.gate;

import com.tepeu.runtime.answer.Consent;
import com.tepeu.runtime.answer.Fee;
import com.tepeu.runtime.answer.Fields;
import com.tepeu.runtime.answer.Range;
import com.tepeu.runtime.answer.Stop;
import com.tepeu.runtime.ledger.Ledger;
import com.tepeu.runtime.word.Ability;
import com.tepeu.runtime.word.Field;
import com.tepeu.runtime.word.Kind;
import com.tepeu.runtime.word.Refusal;
import com.tepeu.runtime.word.Reply;
import com.tepeu.runtime.word.ResultKind;
import com.tepeu.runtime.word.Writer;
import com.tepeu.runtime.work.Attempt;
import com.tepeu.runtime.work.Job;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 检查口按次序问，再决定落不落笔。
 * 做完没做完不由这里写。
 */
class GateTest {

    /** 名字没交进来，先是没装上，不去问费用。 */
    @Test
    void 没装上就不问费用() {
        Ledger ledger = new Ledger();
        Fee fee = new Fee(3);
        Gate gate = gate(ledger, fee, null, List.of());
        ReleaseMark mark = gate.release(Ability.ASK, "g1", true, "", "", "", "", List.of("总则"));
        assertEquals(Refusal.NOT_INSTALLED, mark.outcome());
        assertTrue(hasCode(ledger, Refusal.NOT_INSTALLED));
        assertFalse(hasCode(ledger, Refusal.GATE_BLOCKED));
        assertEquals(3, fee.left());
    }

    /** 问模型交了、费用没交，拦住，不去调用。 */
    @Test
    void 没有费用不能问模型() {
        Ledger ledger = new Ledger();
        AtomicInteger called = new AtomicInteger();
        Gate gate = gate(ledger, null, null, List.of(asking(called, Reply.success("有字"))));
        ReleaseMark mark = gate.release(Ability.ASK, "g1", true, "", "", "", "", List.of("总则"));
        assertEquals(Refusal.GATE_BLOCKED, mark.outcome());
        assertEquals(0, called.get());
        assertFalse(returned(ledger));
    }

    /** 没有字改记失败。编号要抄上。不写进正题时，步骤记录不动。 */
    @Test
    void 问模型没有字算失败() {
        Ledger ledger = new Ledger();
        Gate gate = gate(ledger, new Fee(1), null, List.of(asking(new AtomicInteger(), Reply.success("  "))));
        assertEquals(ResultKind.失败.name(), gate.release(Ability.ASK, "g1", true, "", "", "", "", List.of("总则")).outcome());
        assertTrue(ledger.read().stream().anyMatch(step -> Kind.RETURN.equals(step.kind())
                && step.resultKind() == ResultKind.失败
                && "g1".equals(step.goalId())));
        int size = ledger.read().size();
        Gate quiet = gate(ledger, new Fee(1), null, List.of(asking(new AtomicInteger(), Reply.success("有字"))));
        quiet.release(Ability.ASK, null, false, "", "", "", "", List.of("总则"));
        assertEquals(size, ledger.read().size());
    }

    /** 没有交回结果，不能当成成功。 */
    @Test
    void 没有交回不算成功() {
        Ledger ledger = new Ledger();
        Gate gate = gate(ledger, new Fee(1), null, List.of(new Job() {
            public String name() {
                return Ability.READ;
            }

            public Reply run(Attempt attempt) {
                return null;
            }
        }));
        assertEquals(ResultKind.失败.name(), gate.release(Ability.READ, "g1", true, "这里", "这里", "", "", List.of()).outcome());
        assertTrue(ledger.read().stream().anyMatch(step -> Kind.RETURN.equals(step.kind()) && step.resultKind() == ResultKind.失败));
    }

    /** 目标不在里面，不去问点头，也不去写。 */
    @Test
    void 超出范围不问点头(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("in.txt");
        Files.writeString(file, "字");
        Ledger ledger = new Ledger();
        AtomicInteger consentCalls = new AtomicInteger();
        AtomicInteger writes = new AtomicInteger();
        Consent consent = new Consent((name, content) -> {
            consentCalls.incrementAndGet();
            return Consent.Answer.同意;
        });
        Gate gate = new Gate(ledger, new Stop(null), new Fields(), new Range(), new Fee(1), consent,
                List.of(new Job() {
                    public String name() {
                        return Ability.WRITE;
                    }

                    public Reply run(Attempt attempt) {
                        writes.incrementAndGet();
                        return Reply.success("写了");
                    }
                }));
        ReleaseMark mark = gate.release(Ability.WRITE, "g1", true, dir.resolve("..").resolve("out.txt").toString(),
                dir.toString(), "要做什么", "看见文件", List.of());
        assertEquals(Refusal.GATE_BLOCKED, mark.outcome());
        assertEquals(0, consentCalls.get());
        assertEquals(0, writes.get());
    }

    /** 点头没交、拒绝、没结果、出错，四种结果分开记。 */
    @Test
    void 点头的四种结果(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("a.txt");
        Files.writeString(file, "字");
        assertEquals(Refusal.GATE_BLOCKED, releaseWrite(dir, file, null).outcome());
        assertEquals(Refusal.USER_DENIED, releaseWrite(dir, file, (name, content) -> Consent.Answer.拒绝).outcome());
        assertEquals(Refusal.APPROVAL_TIMEOUT, releaseWrite(dir, file, (name, content) -> null).outcome());
        assertEquals(Refusal.GATE_BLOCKED, releaseWrite(dir, file, (name, content) -> {
            throw new IllegalStateException("出错");
        }).outcome());
    }

    /** 没有执行登记，不能写已安排。 */
    @Test
    void 没有执行登记不能写已安排() {
        Ledger ledger = new Ledger();
        Gate gate = gate(ledger, new Fee(1), null, List.of());
        assertFalse(gate.arrange(true));
        assertFalse(ledger.read().stream().anyMatch(step -> Kind.ARRANGED.equals(step.kind())));
    }

    /** 字段缺了是待补全，不合格是拦住。 */
    @Test
    void 字段两种拦住() {
        Ledger ledger = new Ledger();
        Gate gate = gate(ledger, new Fee(1), null, List.of());
        assertFalse(gate.openable(Field.of("做事"), Field.of("这里"), Field.missing()));
        assertTrue(ledger.read().stream().anyMatch(step -> Kind.AWAITING.equals(step.kind()) && step.writer() == Writer.gate));
        assertFalse(gate.openable(Field.of("按上面说的做"), Field.of("这里"), Field.of("看见")));
        assertTrue(hasCode(ledger, Refusal.GATE_BLOCKED));
    }

    private static ReleaseMark releaseWrite(Path dir, Path file, Consent.Decider decider) {
        Ledger ledger = new Ledger();
        Consent consent = decider == null ? null : new Consent(decider);
        Gate gate = new Gate(ledger, new Stop(null), new Fields(), new Range(), new Fee(1), consent,
                List.of(new Job() {
                    public String name() {
                        return Ability.WRITE;
                    }

                    public Reply run(Attempt attempt) {
                        return Reply.success("写了");
                    }
                }));
        return gate.release(Ability.WRITE, "g1", true, file.toString(), file.toString(), "要做什么", "看见", List.of());
    }

    private static Gate gate(Ledger ledger, Fee fee, Consent consent, List<Job> jobs) {
        return new Gate(ledger, new Stop(null), new Fields(), new Range(), fee, consent, jobs);
    }

    private static Job asking(AtomicInteger called, Reply reply) {
        return new Job() {
            public String name() {
                return Ability.ASK;
            }

            public Reply run(Attempt attempt) {
                called.incrementAndGet();
                return reply;
            }
        };
    }

    private static boolean hasCode(Ledger ledger, String code) {
        return ledger.read().stream().anyMatch(step -> code.equals(step.refusalCode()));
    }

    private static boolean returned(Ledger ledger) {
        return ledger.read().stream().anyMatch(step -> Kind.RETURN.equals(step.kind()));
    }
}
