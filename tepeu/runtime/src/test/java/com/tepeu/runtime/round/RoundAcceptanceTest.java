package com.tepeu.runtime.round;

import com.tepeu.runtime.answer.Consent;
import com.tepeu.runtime.answer.Fee;
import com.tepeu.runtime.answer.Fields;
import com.tepeu.runtime.answer.Range;
import com.tepeu.runtime.answer.Stop;
import com.tepeu.runtime.word.Ability;
import com.tepeu.runtime.word.Entry;
import com.tepeu.runtime.word.Field;
import com.tepeu.runtime.word.Kind;
import com.tepeu.runtime.word.Refusal;
import com.tepeu.runtime.word.Reply;
import com.tepeu.runtime.word.ResultKind;
import com.tepeu.runtime.word.Writer;
import com.tepeu.runtime.work.AskModel;
import com.tepeu.runtime.work.Job;
import com.tepeu.runtime.work.ReadFile;
import com.tepeu.runtime.work.RunProgram;
import com.tepeu.runtime.work.WriteFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 从门外把一轮、做事、回答的验收走一遍。
 * 做完只看步骤记录，走过的步骤只看过程记录。
 */
class RoundAcceptanceTest {

    /** 不合格原句、空白、没装、模型嘴上的完成，都不能把事做起来。 */
    @Test
    void 正题几种开不了() {
        assertTrue(code(run(Field.of("按上面说的做"), Field.of("这里"), Field.of("看见"), List.of(), 1, List.of()), Refusal.GATE_BLOCKED));
        assertFalse(has(run(Field.of("   "), Field.of("这里"), Field.of("看见"), List.of(), 1, List.of()), Kind.AWAITING));
        assertTrue(code(run(Field.of("做事"), Field.of("这里"), Field.of("完成"), List.of(), 1, List.of()), Refusal.GATE_BLOCKED));

        Door missing = run(Field.of("做事"), Field.of("这里"), Field.of("看见"), List.of(Call.of("没有")), 1, List.of());
        assertTrue(code(missing, Refusal.NOT_INSTALLED));
        assertTrue(writer(missing, Kind.NOT_DONE, Writer.judge));
        assertTrue(writer(missing, Kind.PIECE, Writer.base));

        Door said = run(Field.of("做事"), Field.of("这里"), Field.of("看见"), List.of(), 1, List.of());
        assertFalse(said.ledger().stream().anyMatch(step -> "已完成".equals(step.body()) || "已安排".equals(step.body()) || Kind.ARRANGED.equals(step.kind())));
        assertTrue(has(said, Kind.NOT_DONE));
        assertFalse(has(said, Kind.DONE));
    }

    /** 第八个拦住，第九个跳过。问模型成功不能算做完。 */
    @Test
    void 次数和问模型() {
        List<Call> eight = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            eight.add(Call.of("没有"));
        }
        Door full = run(Field.of("做事"), Field.of("这里"), Field.of("看见"), eight, 1, List.of());
        assertEquals(8, countCode(full, Refusal.NOT_INSTALLED));
        assertTrue(has(full, Kind.NOT_DONE));

        List<Call> nine = new ArrayList<>(eight);
        nine.add(Call.of("第九个"));
        Door over = run(Field.of("做事"), Field.of("这里"), Field.of("看见"), nine, 1, List.of());
        assertEquals(8, countCode(over, Refusal.NOT_INSTALLED));
        assertEquals(8, over.processLog().stream().filter(note -> ProcessNote.RELEASE.equals(note.step())).count());
        assertTrue(over.processLog().stream().anyMatch(note -> ProcessNote.SKIP.equals(note.step()) && "第九个".equals(note.detail())));

        int[] asked = {0};
        Door success = run(Field.of("做事"), Field.of("这里"), Field.of("看见"), List.of(Call.of(Ability.ASK)), 1,
                List.of(new AskModel(lines -> {
                    asked[0] = asked[0] + 1;
                    return Reply.success("g999");
                })));
        assertEquals(1, asked[0]);
        assertTrue(success.ledger().stream().anyMatch(step -> Kind.RETURN.equals(step.kind())
                && step.resultKind() == ResultKind.成功
                && "g1".equals(step.goalId())
                && "g999".equals(step.body())));
        assertTrue(has(success, Kind.NOT_DONE));
        assertFalse(has(success, Kind.DONE));

        Door blank = run(Field.of("做事"), Field.of("这里"), Field.of("看见"), List.of(Call.of(Ability.ASK)), 1,
                List.of(new AskModel(lines -> Reply.success("  "))));
        assertTrue(blank.ledger().stream().anyMatch(step -> Kind.RETURN.equals(step.kind()) && step.resultKind() == ResultKind.失败));
        assertFalse(has(blank, Kind.DONE));
    }

    /** 只聊天和旁边问不记任务，也不判断。正题不看要不要问模型这个标记。 */
    @Test
    void 只聊天和旁边问() {
        Fee chatFee = new Fee(1);
        Door chatBlocked = Door.open(new Stop(null), new Fields(), new Range(), null,
                new Consent((name, content) -> Consent.Answer.同意),
                List.of(new AskModel(lines -> Reply.success("有字"))), null);
        chatBlocked.run(chat(true));
        assertTrue(code(chatBlocked, Refusal.GATE_BLOCKED));
        assertFalse(has(chatBlocked, Kind.RETURN));
        assertFalse(has(chatBlocked, Kind.DONE));
        assertTrue(chatBlocked.processLog().stream().anyMatch(note -> ProcessNote.CHAT.equals(note.step())));
        assertTrue(chatBlocked.processLog().stream().anyMatch(note -> ProcessNote.LOAD.equals(note.step())));

        Door chatted = Door.open(new Stop(null), new Fields(), new Range(), chatFee,
                new Consent((name, content) -> Consent.Answer.同意),
                List.of(new AskModel(lines -> Reply.success("有字")), new WriteFile()), null);
        chatted.run(new Turn(Entry.main, true, false, "", true, Field.missing(), Field.missing(), Field.missing(),
                List.of(Call.of(Ability.WRITE, "不该写")), ""));
        assertTrue(chatted.ledger().stream().anyMatch(step -> Kind.RETURN.equals(step.kind()) && step.goalId() == null));
        assertFalse(has(chatted, Kind.PIECE));
        assertEquals(0, chatFee.left());

        Door side = Door.open(new Stop(null), new Fields(), new Range(), new Fee(1),
                new Consent((name, content) -> Consent.Answer.同意),
                List.of(new AskModel(lines -> Reply.success("有字")), new ReadFile()), null);
        side.run(new Turn(Entry.side, true, false, "", false, Field.missing(), Field.missing(), Field.missing(),
                List.of(Call.of(Ability.READ, "不该读")), ""));
        assertFalse(has(side, Kind.PIECE));
        assertFalse(has(side, Kind.RETURN));
        assertFalse(has(side, Kind.DONE));
        assertTrue(side.processLog().stream().anyMatch(note -> ProcessNote.SIDE.equals(note.step())));
        assertFalse(side.processLog().stream().anyMatch(note -> ProcessNote.CHAT.equals(note.step())));

        int[] sideAsked = {0};
        Door sideAsk = Door.open(new Stop(null), new Fields(), new Range(), null,
                new Consent((name, content) -> Consent.Answer.同意),
                List.of(new AskModel(lines -> {
                    sideAsked[0] = sideAsked[0] + 1;
                    return Reply.success("有字");
                })), null);
        sideAsk.run(new Turn(Entry.side, false, false, "", true, Field.missing(), Field.missing(), Field.missing(), List.of(), ""));
        assertEquals(0, sideAsked[0]);
        assertFalse(has(sideAsk, Kind.RETURN));
        assertTrue(sideAsk.processLog().stream().anyMatch(note -> ProcessNote.RELEASE.equals(note.step()) && note.detail().contains(Refusal.GATE_BLOCKED)));

        Fee untouched = new Fee(2);
        Door notInstalled = Door.open(new Stop(null), new Fields(), new Range(), untouched,
                new Consent((name, content) -> Consent.Answer.同意), List.of(), null);
        notInstalled.run(main(Field.of("做事"), Field.of("这里"), Field.of("看见"), List.of(Call.of(Ability.ASK))));
        assertEquals(2, untouched.left());
        assertTrue(code(notInstalled, Refusal.NOT_INSTALLED));
        assertFalse(code(notInstalled, Refusal.GATE_BLOCKED));

        Door ignored = run(Field.of("做事"), Field.of("这里"), Field.of("看见"), List.of(), 1,
                List.of(new AskModel(lines -> Reply.success("不该问"))));
        assertFalse(has(ignored, Kind.RETURN));
    }

    /** 人的原话才能停。模型说停、换掉的词表、全角英文，都按词表认。 */
    @Test
    void 停止只认原话() {
        assertEquals(List.of(new ProcessNote(ProcessNote.STOP_CHECK, "主轮停")),
                stopped(null, false, "停").processLog());
        assertTrue(has(stopped(null, false, "停，然后继续"), Kind.AWAITING));
        assertFalse(has(stopped(null, false, "停，然后继续"), Kind.STOP));
        assertTrue(has(stopped(null, false, "  STOP!!! "), Kind.STOP));
        assertFalse(has(stopped(null, false, ""), Kind.STOP));
        assertFalse(has(stopped(null, false, "\uFF33\uFF34\uFF2F\uFF30"), Kind.STOP));
        assertFalse(has(stopped(List.of("取消"), false, "停"), Kind.STOP));
        assertFalse(has(stopped(List.of(), false, "停"), Kind.STOP));
        assertTrue(has(stopped(List.of(), true, "继续吧"), Kind.STOP));
        assertFalse(has(stopped(null, false, "！！！"), Kind.STOP));

        Door side = Door.open(new Stop(null), new Fields(), new Range(), new Fee(1),
                new Consent((name, content) -> Consent.Answer.同意), List.of(), null);
        side.run(new Turn(Entry.side, false, false, "停", true, Field.missing(), Field.missing(), Field.missing(), List.of(), "停"));
        assertFalse(has(side, Kind.STOP));
        assertEquals(List.of(new ProcessNote(ProcessNote.STOP_CHECK, "旁问停")), side.processLog());
    }

    /** 写和跑要人点头。读不用。同意不留给下一份。 */
    @Test
    void 点头只对这一份(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("out.txt");
        String goal = "把这句话写进去";
        int[] asked = {0};
        Consent.Decider decider = (name, content) -> {
            asked[0] = asked[0] + 1;
            return asked[0] == 1 ? Consent.Answer.同意 : Consent.Answer.拒绝;
        };
        Door door = Door.open(new Stop(null), new Fields(), new Range(), new Fee(1), new Consent(decider),
                List.of(new WriteFile()), null);
        door.run(main(Field.of(goal), Field.of(file.toString()), Field.of("看见"), List.of(Call.of(Ability.WRITE, file.toString()))));
        assertEquals(goal, Files.readString(file));
        assertTrue(writer(door, Kind.DONE, Writer.judge));
        door.run(main(Field.of(goal), Field.of(file.toString()), Field.of("看见"), List.of(Call.of(Ability.WRITE, file.toString()))));
        assertTrue(code(door, Refusal.USER_DENIED));
        assertEquals(1, door.ledger().stream().filter(step -> Kind.RETURN.equals(step.kind())).count());
        assertTrue(door.processLog().stream().anyMatch(note -> note.detail().contains("写东西 " + Refusal.USER_DENIED)));
        assertFalse(door.processLog().stream().anyMatch(note -> ProcessNote.STOP_CHECK.equals(note.step()) && "主轮停".equals(note.detail())));

        Door noConsent = Door.open(new Stop(null), new Fields(), new Range(), new Fee(1), null, List.of(new WriteFile(), new RunProgram()), null);
        noConsent.run(main(Field.of("做事"), Field.of("这里"), Field.of("看见"), List.of(Call.of(Ability.WRITE, "这里"))));
        assertTrue(code(noConsent, Refusal.GATE_BLOCKED));
        assertFalse(has(noConsent, Kind.RETURN));

        Door readOnly = Door.open(new Stop(null), new Fields(), new Range(), new Fee(1), null, List.of(new ReadFile()), null);
        Files.writeString(file, "文件里的字");
        readOnly.run(main(Field.of("做事"), Field.of(file.toString()), Field.of("看见"), List.of(Call.of(Ability.READ, file.toString()))));
        assertTrue(readOnly.ledger().stream().anyMatch(step -> Kind.RETURN.equals(step.kind()) && "文件里的字".equals(step.body())));
        assertTrue(has(readOnly, Kind.DONE));
    }

    /** 读在目录里可以，逃出去不行。跑本机程序成功才算做完。 */
    @Test
    void 读和跑(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("note.txt");
        Files.writeString(file, "目录里的字");
        Door read = Door.open(new Stop(null), new Fields(), new Range(), new Fee(1),
                new Consent((name, content) -> Consent.Answer.同意), List.of(new ReadFile()), null);
        read.run(main(Field.of("做事"), Field.of(dir.toString()), Field.of("看见"), List.of(Call.of(Ability.READ, file.toString()))));
        assertTrue(read.ledger().stream().anyMatch(step -> "目录里的字".equals(step.body()) && "g1".equals(step.goalId())));
        assertTrue(has(read, Kind.DONE));

        int[] consent = {0};
        Door escape = Door.open(new Stop(null), new Fields(), new Range(), new Fee(1),
                new Consent((name, content) -> {
                    consent[0] = consent[0] + 1;
                    return Consent.Answer.同意;
                }), List.of(new WriteFile()), null);
        escape.run(main(Field.of("做事"), Field.of(dir.toString()), Field.of("看见"),
                List.of(Call.of(Ability.WRITE, dir.resolve("..").resolve("escape.txt").toString()))));
        assertEquals(0, consent[0]);
        assertTrue(code(escape, Refusal.GATE_BLOCKED));

        String hostname = System.getenv("SystemRoot") + "\\System32\\hostname.exe";
        Door ran = Door.open(new Stop(null), new Fields(), new Range(), new Fee(1),
                new Consent((name, content) -> Consent.Answer.同意), List.of(new RunProgram()), null);
        ran.run(main(Field.of("做事"), Field.of(hostname), Field.of("看见"), List.of(Call.of(Ability.RUN, hostname))));
        assertTrue(ran.ledger().stream().anyMatch(step -> Kind.RETURN.equals(step.kind())
                && step.resultKind() == ResultKind.成功
                && step.body() != null
                && !step.body().isBlank()));
        assertTrue(has(ran, Kind.DONE));
    }

    /** 合格的一次读，过程记录按固定次序走，并且标明不是人说的。 */
    @Test
    void 过程记录的次序() {
        Door door = run(Field.of("做事"), Field.of("这里"), Field.of("看见"), List.of(Call.of(Ability.READ, "这里")), 1,
                List.of(reader("文件里的字")));
        List<ProcessNote> notes = door.processLog();
        assertEquals(ProcessNote.STOP_CHECK, notes.get(0).step());
        assertEquals("继续", notes.get(0).detail());
        assertEquals(ProcessNote.PIECE, notes.get(1).step());
        assertEquals(ProcessNote.LOAD, notes.get(2).step());
        assertTrue(notes.get(2).detail().contains("总则·不是人说的"));
        assertTrue(notes.get(2).detail().contains("这一件·不是人说的"));
        assertEquals(ProcessNote.RELEASE, notes.get(3).step());
        assertEquals("读东西 成功", notes.get(3).detail());
        assertEquals(ProcessNote.TO_JUDGE, notes.get(4).step());
        assertFalse(door.ledger().stream().anyMatch(step -> "装载".equals(step.kind())));
        assertTrue(has(door, Kind.DONE));
    }

    /** 开始之后没有再交能力的入口。下一轮的过程记录不带着上一轮。 */
    @Test
    void 门外不能再装() {
        for (Method method : Door.class.getDeclaredMethods()) {
            assertTrue(Set.of("open", "run", "ledger", "processLog").contains(method.getName()));
        }
        Door door = Door.open(new Stop(null), new Fields(), new Range(), new Fee(1),
                new Consent((name, content) -> Consent.Answer.同意), List.of(), null);
        door.run(new Turn(Entry.main, false, true, "停", false, Field.missing(), Field.missing(), Field.missing(), List.of(), ""));
        door.run(new Turn(Entry.main, true, false, "", false, Field.missing(), Field.missing(), Field.missing(), List.of(), ""));
        assertFalse(door.processLog().stream().anyMatch(note -> "主轮停".equals(note.detail())));
        assertTrue(door.processLog().stream().anyMatch(note -> ProcessNote.CHAT.equals(note.step())));
        assertEquals(1, door.ledger().stream().filter(step -> Kind.WHO.equals(step.kind())).count());
    }

    /** 费用、点头、范围的次序不能倒。回答正文里的能力名不会再被调用。 */
    @Test
    void 放行次序和换内容(@TempDir Path dir) throws Exception {
        int[] asked = {0};
        Door noFee = Door.open(new Stop(null), new Fields(), new Range(), null,
                new Consent((name, content) -> Consent.Answer.同意),
                List.of(new AskModel(lines -> {
                    asked[0] = asked[0] + 1;
                    return Reply.success("有字");
                })), null);
        noFee.run(main(Field.of("做事"), Field.of("这里"), Field.of("看见"), List.of(Call.of(Ability.ASK))));
        assertEquals(0, asked[0]);
        assertTrue(code(noFee, Refusal.GATE_BLOCKED));
        assertFalse(code(noFee, Refusal.NOT_INSTALLED));
        assertFalse(has(noFee, Kind.RETURN));
        assertTrue(writer(noFee, Kind.NOT_DONE, Writer.judge));

        int[] sideCalls = {0};
        Door side = Door.open(new Stop(null), new Fields(), new Range(), new Fee(1),
                new Consent((name, content) -> Consent.Answer.同意),
                List.of(new AskModel(lines -> {
                    sideCalls[0] = sideCalls[0] + 1;
                    return Reply.success("有字");
                })), null);
        side.run(new Turn(Entry.side, true, false, "", true, Field.missing(), Field.missing(), Field.missing(), List.of(), ""));
        assertEquals(1, sideCalls[0]);
        assertFalse(has(side, Kind.RETURN));
        assertFalse(has(side, Kind.PIECE));
        assertFalse(has(side, Kind.DONE));
        assertTrue(side.processLog().stream().anyMatch(note -> ProcessNote.SIDE.equals(note.step())));
        assertTrue(side.processLog().stream().anyMatch(note -> ProcessNote.LOAD.equals(note.step())));
        assertFalse(side.processLog().stream().anyMatch(note -> ProcessNote.CHAT.equals(note.step())));

        int[] times = {0};
        Fee once = new Fee(1);
        Door twice = Door.open(new Stop(null), new Fields(), new Range(), once,
                new Consent((name, content) -> Consent.Answer.同意),
                List.of(new AskModel(lines -> {
                    times[0] = times[0] + 1;
                    assertTrue(lines.contains("总则"));
                    return Reply.success("读东西");
                }), new ReadFile()), null);
        twice.run(main(Field.of("做事"), Field.of("这里"), Field.of("看见"), List.of(Call.of(Ability.ASK))));
        twice.run(main(Field.of("做事"), Field.of("这里"), Field.of("看见"), List.of(Call.of(Ability.ASK))));
        assertEquals(1, times[0]);
        assertEquals(0, once.left());
        assertEquals(1, twice.ledger().stream().filter(step -> Kind.RETURN.equals(step.kind()) && step.resultKind() == ResultKind.成功).count());
        assertTrue(code(twice, Refusal.GATE_BLOCKED));

        Path first = dir.resolve("a.txt");
        Path second = dir.resolve("b.txt");
        int[] consent = {0};
        Door changed = Door.open(new Stop(null), new Fields(), new Range(), new Fee(1),
                new Consent((name, content) -> {
                    consent[0] = consent[0] + 1;
                    return content.contains("第二件") ? Consent.Answer.同意 : Consent.Answer.拒绝;
                }), List.of(new WriteFile()), null);
        changed.run(main(Field.of("第一件"), Field.of(first.toString()), Field.of("看见"), List.of(Call.of(Ability.WRITE, first.toString()))));
        changed.run(main(Field.of("第二件"), Field.of(second.toString()), Field.of("看见"), List.of(Call.of(Ability.WRITE, second.toString()))));
        assertEquals(2, consent[0]);
        assertEquals("第二件", Files.readString(second));
        assertFalse(Files.exists(first));
        assertTrue(code(changed, Refusal.USER_DENIED));
        assertTrue(changed.ledger().stream().anyMatch(step -> Kind.RETURN.equals(step.kind()) && "第二件".equals(step.body())));

        int[] nodded = {0};
        Door blankTarget = Door.open(new Stop(null), new Fields(), new Range(), new Fee(1),
                new Consent((name, content) -> {
                    nodded[0] = nodded[0] + 1;
                    return Consent.Answer.同意;
                }), List.of(new WriteFile()), null);
        blankTarget.run(main(Field.of("做事"), Field.of("这里"), Field.of("看见"), List.of(Call.of(Ability.WRITE))));
        assertEquals(0, nodded[0]);
        assertTrue(code(blankTarget, Refusal.GATE_BLOCKED));

        Door missingRead = Door.open(new Stop(null), new Fields(), new Range(), new Fee(1),
                new Consent((name, content) -> Consent.Answer.同意), List.of(), null);
        missingRead.run(main(Field.of("做事"), Field.of("同一个"), Field.of("看见"), List.of(Call.of(Ability.READ, "同一个"))));
        assertTrue(code(missingRead, Refusal.NOT_INSTALLED));
        assertFalse(code(missingRead, Refusal.GATE_BLOCKED));

        Door closed = run(Field.of("按上面说的做"), Field.of("这里"), Field.of("看见"), List.of(Call.of(Ability.ASK)), 1,
                List.of(new AskModel(lines -> Reply.success("不该问"))));
        assertFalse(closed.processLog().stream().anyMatch(note -> ProcessNote.LOAD.equals(note.step())));
        assertFalse(has(closed, Kind.RETURN));
    }

    private static Door run(Field goal, Field limit, Field doneWhen, List<Call> calls, int times, List<Job> jobs) {
        Door door = Door.open(new Stop(null), new Fields(), new Range(), new Fee(times),
                new Consent((name, content) -> Consent.Answer.同意), jobs, null);
        door.run(main(goal, limit, doneWhen, calls));
        return door;
    }

    private static Door stopped(List<String> words, boolean stopHit, String said) {
        Door door = Door.open(new Stop(words), new Fields(), new Range(), new Fee(1),
                new Consent((name, content) -> Consent.Answer.同意), List.of(), null);
        door.run(new Turn(Entry.main, false, stopHit, said, false, Field.missing(), Field.missing(), Field.missing(), List.of(), "停"));
        return door;
    }

    private static Turn main(Field goal, Field limit, Field doneWhen, List<Call> calls) {
        return new Turn(Entry.main, false, false, "", true, goal, limit, doneWhen, calls, "已完成");
    }

    private static Turn chat(boolean askModel) {
        return new Turn(Entry.main, true, false, "", askModel, Field.missing(), Field.missing(), Field.missing(), List.of(), "");
    }

    private static Job reader(String body) {
        return new Job() {
            public String name() {
                return Ability.READ;
            }

            public Reply run(com.tepeu.runtime.work.Attempt attempt) {
                return Reply.success(body);
            }
        };
    }

    private static boolean has(Door door, String kind) {
        return door.ledger().stream().anyMatch(step -> kind.equals(step.kind()));
    }

    private static boolean code(Door door, String refusal) {
        return door.ledger().stream().anyMatch(step -> refusal.equals(step.refusalCode()));
    }

    private static long countCode(Door door, String refusal) {
        return door.ledger().stream().filter(step -> refusal.equals(step.refusalCode())).count();
    }

    private static boolean writer(Door door, String kind, Writer writer) {
        return door.ledger().stream().anyMatch(step -> kind.equals(step.kind()) && step.writer() == writer);
    }
}
