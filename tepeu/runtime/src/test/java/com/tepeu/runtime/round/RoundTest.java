package com.tepeu.runtime.round;

import com.tepeu.runtime.answer.Consent;
import com.tepeu.runtime.answer.Fee;
import com.tepeu.runtime.answer.Fields;
import com.tepeu.runtime.answer.Range;
import com.tepeu.runtime.answer.Stop;
import com.tepeu.runtime.ledger.Step;
import com.tepeu.runtime.word.Ability;
import com.tepeu.runtime.word.Entry;
import com.tepeu.runtime.word.Field;
import com.tepeu.runtime.word.Kind;
import com.tepeu.runtime.word.Refusal;
import com.tepeu.runtime.word.Reply;
import com.tepeu.runtime.word.ResultKind;
import com.tepeu.runtime.word.Writer;
import com.tepeu.runtime.work.Attempt;
import com.tepeu.runtime.work.Job;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 一轮和外面那一道门。
 * 做完没有只从步骤记录读，走过的步骤只从过程记录读。
 */
class RoundTest {

    /** 装好就能看见谁。跑完这一次调用本身不交回做完。 */
    @Test
    void 门外只读两本记录() {
        Door door = open(1, List.of(readOk()), Consent.Answer.同意);
        assertTrue(door.ledger().stream().anyMatch(step -> Kind.WHO.equals(step.kind()) && step.writer() == Writer.base));
        door.run(main(Field.of("做事"), Field.of("这里"), Field.of("看见"), List.of(Call.of(Ability.READ, "这里")), false, ""));
        assertTrue(door.ledger().stream().anyMatch(step -> Kind.DONE.equals(step.kind())));
        assertTrue(door.processLog().stream().anyMatch(note -> ProcessNote.TO_JUDGE.equals(note.step())));
    }

    /** 正题停：步骤记录有停，过程记录只有主轮停。 */
    @Test
    void 正题停() {
        Door door = open(1, List.of(), Consent.Answer.同意);
        door.run(new Turn(Entry.main, false, true, "停", true, Field.missing(), Field.missing(), Field.missing(), List.of(), "停"));
        assertTrue(has(door, Kind.STOP));
        assertFalse(has(door, Kind.DONE));
        assertFalse(has(door, Kind.NOT_DONE));
        assertEquals(List.of(new ProcessNote(ProcessNote.STOP_CHECK, "主轮停")), door.processLog());
    }

    /** 只聊天并且不问模型，不看字段。过程里有纯聊和装载。 */
    @Test
    void 只聊天() {
        Door door = open(1, List.of(), Consent.Answer.同意);
        door.run(new Turn(Entry.main, true, false, "", false, Field.missing(), Field.missing(), Field.missing(), List.of(), ""));
        assertFalse(has(door, Kind.AWAITING));
        assertFalse(code(door, Refusal.GATE_BLOCKED));
        assertFalse(has(door, Kind.DONE));
        assertTrue(door.processLog().stream().anyMatch(note -> ProcessNote.CHAT.equals(note.step())));
        assertTrue(door.processLog().stream().anyMatch(note -> ProcessNote.LOAD.equals(note.step())));
    }

    /** 没提交怎样算做完，是待补全，过程记未开跑。 */
    @Test
    void 待补全() {
        Door door = open(1, List.of(readOk()), Consent.Answer.同意);
        door.run(main(Field.of("做事"), Field.of("这里"), Field.missing(), List.of(Call.of(Ability.READ, "这里")), false, ""));
        assertTrue(has(door, Kind.AWAITING));
        assertFalse(has(door, Kind.NOT_DONE));
        assertTrue(door.processLog().stream().anyMatch(note -> ProcessNote.CLOSED.equals(note.step())));
    }

    /** 模型说已完成，名单是空的，没做完。这句话不进能力交回。 */
    @Test
    void 模型说完成不算() {
        Door door = open(1, List.of(), Consent.Answer.同意);
        door.run(main(Field.of("做事"), Field.of("这里"), Field.of("看见"), List.of(), false, "已完成"));
        assertFalse(door.ledger().stream().anyMatch(step -> "已完成".equals(step.body())));
        assertTrue(has(door, Kind.NOT_DONE));
        assertFalse(has(door, Kind.DONE));
    }

    /** 不是问模型的成功，编号对上，做完。装载不进步骤记录。 */
    @Test
    void 读成功算做完() {
        Door door = open(1, List.of(readOk()), Consent.Answer.同意);
        door.run(main(Field.of("做事"), Field.of("这里"), Field.of("看见"), List.of(Call.of(Ability.READ, "这里")), false, ""));
        String goalId = piece(door);
        assertTrue(door.ledger().stream().anyMatch(step -> Kind.RETURN.equals(step.kind())
                && goalId.equals(step.goalId())
                && step.writer() == Writer.gate));
        assertTrue(has(door, Kind.DONE));
        assertFalse(door.ledger().stream().anyMatch(step -> "装载".equals(step.kind())));
        assertTrue(door.processLog().stream().anyMatch(note -> ProcessNote.LOAD.equals(note.step()) && note.detail().contains("不是人说的")));
    }

    /** 失败代码不是完成标准，没做完。对上了就做完。 */
    @Test
    void 失败要整句对上() {
        Door missed = open(1, List.of(readFailed("FAILED")), Consent.Answer.同意);
        missed.run(main(Field.of("做事"), Field.of("这里"), Field.of("看见"), List.of(Call.of(Ability.READ, "这里")), false, ""));
        assertTrue(has(missed, Kind.NOT_DONE));
        Door hit = open(1, List.of(readFailed("FAILED")), Consent.Answer.同意);
        hit.run(main(Field.of("做事"), Field.of("这里"), Field.of("FAILED"), List.of(Call.of(Ability.READ, "这里")), false, ""));
        assertTrue(has(hit, Kind.DONE));
        assertFalse(has(hit, Kind.NOT_DONE));
    }

    /** 旁边问并且停，正题没有停。过程只有旁问停。 */
    @Test
    void 旁边问停() {
        Door door = open(1, List.of(readOk()), Consent.Answer.同意);
        door.run(new Turn(Entry.side, true, true, "停", true, Field.missing(), Field.missing(), Field.missing(), List.of(Call.of(Ability.READ, "这里")), ""));
        assertFalse(has(door, Kind.STOP));
        assertFalse(has(door, Kind.PIECE));
        assertEquals(List.of(new ProcessNote(ProcessNote.STOP_CHECK, "旁问停")), door.processLog());
    }

    /** 第九个不放行。问模型成功不能算做完。 */
    @Test
    void 次数和问模型() {
        List<Call> nine = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            nine.add(Call.of("没有的能力"));
        }
        Door door = open(1, List.of(), Consent.Answer.同意);
        door.run(main(Field.of("做事"), Field.of("这里"), Field.of("看见"), nine, false, ""));
        assertEquals(8, door.ledger().stream().filter(step -> Refusal.NOT_INSTALLED.equals(step.refusalCode())).count());
        assertEquals(8, door.processLog().stream().filter(note -> ProcessNote.RELEASE.equals(note.step())).count());
        assertTrue(door.processLog().stream().anyMatch(note -> ProcessNote.SKIP.equals(note.step())));
        assertTrue(has(door, Kind.NOT_DONE));

        Door asked = open(1, List.of(asking(Reply.success("有字"))), Consent.Answer.同意);
        asked.run(main(Field.of("做事"), Field.of("这里"), Field.of("看见"), List.of(Call.of(Ability.ASK)), false, ""));
        assertTrue(asked.ledger().stream().anyMatch(step -> Kind.RETURN.equals(step.kind()) && step.resultKind() == ResultKind.成功));
        assertTrue(has(asked, Kind.NOT_DONE));
        assertFalse(has(asked, Kind.DONE));
    }

    /** 只聊天问到了模型，不抄任务编号，也不判断。 */
    @Test
    void 只聊天问模型() {
        Door door = open(1, List.of(asking(Reply.success("有字"))), Consent.Answer.同意);
        door.run(new Turn(Entry.main, true, false, "", true, Field.missing(), Field.missing(), Field.missing(), List.of(), ""));
        assertTrue(door.ledger().stream().anyMatch(step -> Kind.RETURN.equals(step.kind()) && step.goalId() == null));
        assertFalse(has(door, Kind.DONE));
        assertFalse(has(door, Kind.NOT_DONE));
    }

    private static Door open(int times, List<Job> jobs, Consent.Answer consent) {
        return Door.open(new Stop(null), new Fields(), new Range(), new Fee(times),
                new Consent((name, content) -> consent), jobs, null);
    }

    private static Turn main(Field goal, Field limit, Field doneWhen, List<Call> calls, boolean stop, String saidByModel) {
        return new Turn(Entry.main, false, stop, "", false, goal, limit, doneWhen, calls, saidByModel);
    }

    private static Job readOk() {
        return new Job() {
            public String name() {
                return Ability.READ;
            }

            public Reply run(Attempt attempt) {
                return Reply.success("文件里的字");
            }
        };
    }

    private static Job readFailed(String code) {
        return new Job() {
            public String name() {
                return Ability.READ;
            }

            public Reply run(Attempt attempt) {
                return new Reply(ResultKind.失败, code, "");
            }
        };
    }

    private static Job asking(Reply reply) {
        return new Job() {
            public String name() {
                return Ability.ASK;
            }

            public Reply run(Attempt attempt) {
                return reply;
            }
        };
    }

    private static boolean has(Door door, String kind) {
        return door.ledger().stream().anyMatch(step -> kind.equals(step.kind()));
    }

    private static boolean code(Door door, String refusal) {
        return door.ledger().stream().anyMatch(step -> refusal.equals(step.refusalCode()));
    }

    private static String piece(Door door) {
        return door.ledger().stream().filter(step -> Kind.PIECE.equals(step.kind())).map(Step::goalId).findFirst().orElse("");
    }
}
