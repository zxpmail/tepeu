package com.tepeu.round;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 一轮闭环验收。每条都从步骤账读回。
 */
class RoundAcceptanceTest {

    private static final Field GOAL = Field.of("把说明写清楚");
    private static final Field LIMIT = Field.of("只改这一份");
    private static final Field DONE_WHEN = Field.of("说明里有结论");
    private static final String READ = "读东西";

    @Test
    void v01停() {
        List<LedgerEntry> rows = run(Set.of(), true, CapabilityReply.success(), turn(
                Entry.main, false, true, false, Field.missing(), Field.missing(), Field.missing(), List.of(), ""));
        assertTrue(rows.stream().anyMatch(row -> LedgerEntry.STOP.equals(row.kind()) && row.writer() == Writer.gate));
        assertFalse(named(rows, Gate.ASK_MODEL));
        assertFalse(has(rows, LedgerEntry.DONE));
        assertFalse(has(rows, LedgerEntry.NOT_DONE));
        checkWriters(rows);
    }

    @Test
    void v02纯聊() {
        List<LedgerEntry> rows = run(Set.of(), true, CapabilityReply.success(), turn(
                Entry.main, true, false, false, Field.missing(), Field.missing(), Field.missing(), List.of(), ""));
        assertFalse(has(rows, LedgerEntry.AWAITING));
        assertFalse(code(rows, LedgerEntry.GATE_BLOCKED));
        assertFalse(has(rows, LedgerEntry.DONE));
        assertFalse(has(rows, LedgerEntry.NOT_DONE));
        assertFalse(named(rows, "写东西"));
        assertFalse(named(rows, "跑命令"));
        checkWriters(rows);
    }

    @Test
    void v03待补全() {
        List<LedgerEntry> rows = run(Set.of(), true, CapabilityReply.success(), turn(
                Entry.main, false, false, false, GOAL, LIMIT, Field.missing(), List.of(READ), ""));
        assertTrue(rows.stream().anyMatch(row -> LedgerEntry.AWAITING.equals(row.kind()) && row.writer() == Writer.gate));
        assertFalse(named(rows, READ));
        assertFalse(has(rows, LedgerEntry.DONE));
        assertFalse(has(rows, LedgerEntry.NOT_DONE));
        assertFalse(code(rows, LedgerEntry.GATE_BLOCKED));
        checkWriters(rows);
    }

    @Test
    void v04指针句() {
        List<LedgerEntry> rows = run(Set.of(), true, CapabilityReply.success(), turn(
                Entry.main, false, false, false, Field.of("按上面说的做"), LIMIT, DONE_WHEN, List.of(READ), ""));
        assertTrue(code(rows, LedgerEntry.GATE_BLOCKED));
        assertFalse(named(rows, READ));
        assertFalse(has(rows, LedgerEntry.DONE));
        assertFalse(has(rows, LedgerEntry.NOT_DONE));
        checkWriters(rows);
    }

    @Test
    void v05未装() {
        List<LedgerEntry> rows = run(Set.of(), true, CapabilityReply.success(), opened(List.of("没有的能力")));
        assertTrue(rows.stream().anyMatch(row -> LedgerEntry.NOT_INSTALLED.equals(row.refusalCode()) && row.writer() == Writer.gate));
        assertTrue(rows.stream().anyMatch(row -> LedgerEntry.PIECE.equals(row.kind()) && row.writer() == Writer.base));
        assertTrue(has(rows, LedgerEntry.NOT_DONE));
        assertFalse(has(rows, LedgerEntry.DONE));
        checkWriters(rows);
    }

    @Test
    void v06模型说完成() {
        List<LedgerEntry> rows = run(Set.of(), true, CapabilityReply.success(), opened(List.of(), "已完成"));
        assertFalse(has(rows, LedgerEntry.RETURN));
        assertTrue(rows.stream().noneMatch(row -> "已完成".equals(row.body())));
        assertTrue(has(rows, LedgerEntry.NOT_DONE));
        assertFalse(has(rows, LedgerEntry.DONE));
        checkWriters(rows);
    }

    @Test
    void v07做成() {
        Round round = program(Set.of(READ), true, CapabilityReply.success());
        round.run(opened(List.of(READ)));
        List<LedgerEntry> rows = round.ledger();
        String goalId = pieceId(rows);
        assertTrue(rows.stream().anyMatch(row -> LedgerEntry.RETURN.equals(row.kind())
                && row.writer() == Writer.gate
                && READ.equals(row.capability())
                && goalId.equals(row.goalId())
                && row.resultKind() == ResultKind.成功));
        assertTrue(has(rows, LedgerEntry.DONE));
        assertTrue(rows.stream().anyMatch(row -> LedgerEntry.PIECE.equals(row.kind())
                && LIMIT.value().equals(row.limit())
                && GOAL.value().equals(row.body())));
        assertEquals(
                List.of(new ProjectionLine("总则", false), new ProjectionLine("这一件", false)),
                round.projection());
        assertFalse(has(rows, "装载"));
        checkWriters(rows);
    }

    @Test
    void v08正文里的编号() {
        Round round = program(Set.of(READ), true, new CapabilityReply(ResultKind.成功, null, "另一个编号 g999"));
        round.run(opened(List.of(READ)));
        List<LedgerEntry> rows = round.ledger();
        String goalId = pieceId(rows);
        assertTrue(rows.stream().anyMatch(row -> LedgerEntry.RETURN.equals(row.kind())
                && goalId.equals(row.goalId())
                && row.body().contains("g999")));
        assertFalse(goalId.equals("g999"));
        assertTrue(has(rows, LedgerEntry.DONE));
        checkWriters(rows);
    }

    @Test
    void v09失败() {
        List<LedgerEntry> rows = run(Set.of(READ), true, new CapabilityReply(ResultKind.失败, "FAILED", ""), opened(List.of(READ)));
        assertTrue(has(rows, LedgerEntry.NOT_DONE));
        assertFalse(has(rows, LedgerEntry.DONE));
        checkWriters(rows);
    }

    @Test
    void v10满八次() {
        List<String> names = names(8);
        List<LedgerEntry> rows = run(Set.of(), true, CapabilityReply.success(), opened(names));
        assertEquals(8, rows.stream().filter(row -> LedgerEntry.NOT_INSTALLED.equals(row.refusalCode())).count());
        assertTrue(has(rows, LedgerEntry.NOT_DONE));
        assertFalse(has(rows, LedgerEntry.DONE));
        checkWriters(rows);
    }

    @Test
    void v11已安排() {
        List<LedgerEntry> rows = run(Set.of(), true, CapabilityReply.success(), opened(List.of(), "已安排"));
        assertTrue(rows.stream().noneMatch(row -> "已安排".equals(row.kind()) || "已安排".equals(row.body())));
        assertTrue(has(rows, LedgerEntry.NOT_DONE));
        assertFalse(has(rows, LedgerEntry.DONE));
        checkWriters(rows);
    }

    @Test
    void v12旁问() {
        List<LedgerEntry> rows = run(Set.of(), true, CapabilityReply.success(), turn(
                Entry.side, false, false, false, GOAL, LIMIT, DONE_WHEN, List.of(READ, "写东西", "跑命令"), ""));
        assertFalse(has(rows, LedgerEntry.PIECE));
        assertFalse(named(rows, READ));
        assertFalse(named(rows, "写东西"));
        assertFalse(named(rows, "跑命令"));
        assertFalse(has(rows, LedgerEntry.DONE));
        assertFalse(has(rows, LedgerEntry.NOT_DONE));
        checkWriters(rows);
    }

    @Test
    void v13写入者() {
        List<LedgerEntry> rows = run(Set.of(), true, CapabilityReply.success(), opened(List.of("没有的能力")));
        assertTrue(rows.stream().anyMatch(row -> LedgerEntry.REFUSAL.equals(row.kind()) && row.writer() == Writer.gate));
        assertTrue(rows.stream().anyMatch(row -> LedgerEntry.PIECE.equals(row.kind()) && row.writer() == Writer.base));
        assertTrue(rows.stream().anyMatch(row -> LedgerEntry.NOT_DONE.equals(row.kind()) && row.writer() == Writer.judge));
        checkWriters(rows);
    }

    @Test
    void v14费用未装() {
        List<LedgerEntry> rows = run(Set.of(Gate.ASK_MODEL), false, CapabilityReply.success(), turn(
                Entry.main, true, false, true, Field.missing(), Field.missing(), Field.missing(), List.of(), ""));
        assertTrue(code(rows, LedgerEntry.GATE_BLOCKED));
        assertFalse(has(rows, LedgerEntry.RETURN));
        assertFalse(has(rows, LedgerEntry.DONE));
        assertFalse(has(rows, LedgerEntry.NOT_DONE));
        checkWriters(rows);
    }

    @Test
    void v15旁问停() {
        List<LedgerEntry> rows = run(Set.of(), true, CapabilityReply.success(), turn(
                Entry.side, false, true, false, Field.missing(), Field.missing(), Field.missing(), List.of(), ""));
        assertFalse(has(rows, LedgerEntry.STOP));
        assertFalse(has(rows, LedgerEntry.PIECE));
        assertFalse(has(rows, LedgerEntry.DONE));
        assertFalse(has(rows, LedgerEntry.NOT_DONE));
        checkWriters(rows);
    }

    @Test
    void v16空白() {
        List<LedgerEntry> rows = run(Set.of(READ), true, CapabilityReply.success(), turn(
                Entry.main, false, false, false, Field.of("   "), LIMIT, DONE_WHEN, List.of(READ), ""));
        assertTrue(code(rows, LedgerEntry.GATE_BLOCKED));
        assertFalse(has(rows, LedgerEntry.AWAITING));
        assertFalse(named(rows, READ));
        assertFalse(has(rows, LedgerEntry.DONE));
        assertFalse(has(rows, LedgerEntry.NOT_DONE));
        checkWriters(rows);
    }

    @Test
    void v17完成算脏单() {
        List<LedgerEntry> rows = run(Set.of(READ), true, CapabilityReply.success(), turn(
                Entry.main, false, false, false, GOAL, LIMIT, Field.of("完成"), List.of(READ), ""));
        assertTrue(code(rows, LedgerEntry.GATE_BLOCKED));
        assertFalse(named(rows, READ));
        assertFalse(has(rows, LedgerEntry.DONE));
        assertFalse(has(rows, LedgerEntry.NOT_DONE));
        checkWriters(rows);
    }

    @Test
    void v18已装但费用未装() {
        List<LedgerEntry> rows = run(Set.of(Gate.ASK_MODEL), false, CapabilityReply.success(), opened(List.of(Gate.ASK_MODEL)));
        assertTrue(code(rows, LedgerEntry.GATE_BLOCKED));
        assertFalse(code(rows, LedgerEntry.NOT_INSTALLED));
        assertFalse(has(rows, LedgerEntry.RETURN));
        assertTrue(has(rows, LedgerEntry.NOT_DONE));
        checkWriters(rows);
    }

    @Test
    void v19未装优先于费用() {
        List<LedgerEntry> rows = run(Set.of(), false, CapabilityReply.success(), opened(List.of(Gate.ASK_MODEL)));
        assertTrue(code(rows, LedgerEntry.NOT_INSTALLED));
        assertFalse(code(rows, LedgerEntry.GATE_BLOCKED));
        assertTrue(has(rows, LedgerEntry.NOT_DONE));
        checkWriters(rows);
    }

    @Test
    void v20第九个不放行() {
        List<String> names = names(9);
        List<LedgerEntry> rows = run(Set.of(), true, CapabilityReply.success(), opened(names));
        assertEquals(8, rows.stream().filter(row -> LedgerEntry.NOT_INSTALLED.equals(row.refusalCode())).count());
        assertFalse(named(rows, names.get(8)));
        assertTrue(has(rows, LedgerEntry.NOT_DONE));
        checkWriters(rows);
    }

    @Test
    void v21旁问费用未装不进主账() {
        Round round = program(Set.of(Gate.ASK_MODEL), false, CapabilityReply.success());
        round.run(turn(
                Entry.side, false, false, true, Field.missing(), Field.missing(), Field.missing(), List.of(), ""));
        List<LedgerEntry> rows = round.ledger();
        assertFalse(code(rows, LedgerEntry.GATE_BLOCKED));
        assertFalse(has(rows, LedgerEntry.RETURN));
        assertFalse(has(rows, LedgerEntry.PIECE));
        assertTrue(round.processLog().contains(new ProcessNote(ProcessNote.SIDE, "问模型")));
        assertTrue(round.processLog().contains(new ProcessNote(ProcessNote.RELEASE, Gate.ASK_MODEL + " " + LedgerEntry.GATE_BLOCKED)));
        checkWriters(rows);
    }

    @Test
    void v22纯聊问模型() {
        List<LedgerEntry> rows = run(Set.of(Gate.ASK_MODEL), true, CapabilityReply.success(), turn(
                Entry.main, true, false, true, Field.missing(), Field.missing(), Field.missing(), List.of(), ""));
        assertTrue(rows.stream().anyMatch(row -> LedgerEntry.RETURN.equals(row.kind())
                && row.writer() == Writer.gate
                && row.goalId() == null));
        assertFalse(has(rows, LedgerEntry.DONE));
        assertFalse(has(rows, LedgerEntry.NOT_DONE));
        checkWriters(rows);
    }

    @Test
    void v23问模型成功不能做成() {
        List<LedgerEntry> rows = run(Set.of(Gate.ASK_MODEL), true, CapabilityReply.success(), opened(List.of(Gate.ASK_MODEL)));
        String goalId = pieceId(rows);
        assertTrue(rows.stream().anyMatch(row -> LedgerEntry.RETURN.equals(row.kind()) && goalId.equals(row.goalId())));
        assertFalse(has(rows, LedgerEntry.DONE));
        assertTrue(has(rows, LedgerEntry.NOT_DONE));
        checkWriters(rows);
    }

    @Test
    void v24旁问优先于纯聊() {
        List<LedgerEntry> rows = run(Set.of(Gate.ASK_MODEL), true, CapabilityReply.success(), turn(
                Entry.side, true, false, true, Field.missing(), Field.missing(), Field.missing(), List.of(), ""));
        assertFalse(has(rows, LedgerEntry.RETURN));
        assertFalse(has(rows, LedgerEntry.PIECE));
        assertFalse(has(rows, LedgerEntry.DONE));
        checkWriters(rows);
    }

    @Test
    void 开跑过程能看回且不做判定() {
        Round round = program(Set.of(READ), true, CapabilityReply.success());
        round.run(opened(List.of(READ)));
        String goalId = pieceId(round.ledger());
        assertEquals(List.of(
                new ProcessNote(ProcessNote.STOP_CHECK, "继续"),
                new ProcessNote(ProcessNote.PIECE, goalId),
                new ProcessNote(ProcessNote.LOAD, "总则·不是人说的、这一件·不是人说的"),
                new ProcessNote(ProcessNote.RELEASE, READ + " 成功"),
                new ProcessNote(ProcessNote.TO_JUDGE, goalId)
        ), round.processLog());
        assertFalse(has(round.ledger(), "装载"));
        assertTrue(has(round.ledger(), LedgerEntry.DONE));
    }

    @Test
    void 第九个记在过程账() {
        List<String> names = names(9);
        Round round = program(Set.of(), true, CapabilityReply.success());
        round.run(opened(names));
        assertTrue(round.processLog().contains(new ProcessNote(ProcessNote.SKIP, names.get(8))));
        assertFalse(named(round.ledger(), names.get(8)));
        assertEquals(8, round.processLog().stream().filter(note -> ProcessNote.RELEASE.equals(note.step())).count());
    }

    @Test
    void 再跑一次投影不重复() {
        Round round = program(Set.of(), true, CapabilityReply.success());
        round.run(opened(List.of()));
        round.run(opened(List.of()));
        assertEquals(
                List.of(new ProjectionLine("总则", false), new ProjectionLine("这一件", false)),
                round.projection());
        assertTrue(round.projection().stream().noneMatch(ProjectionLine::fromPerson));
    }

    @Test
    void 同时写入第二笔记不下来() throws Exception {
        Ledger ledger = new Ledger();
        LedgerEntry extra = new LedgerEntry(
                LedgerEntry.STOP, Writer.gate, null, null, null, null, null, null, null, null);
        java.util.concurrent.atomic.AtomicReference<Throwable> error = new java.util.concurrent.atomic.AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                ledger.append(extra);
            } catch (Throwable thrown) {
                error.set(thrown);
            }
        });
        ledger.runLocked(() -> {
            writer.start();
            try {
                writer.join();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待另一笔写入时被打断", ex);
            }
        });
        assertTrue(error.get() instanceof IllegalStateException);
        assertTrue(ledger.read().isEmpty());
    }

    /** 组成一个程序再跑一轮。 */
    private static List<LedgerEntry> run(Set<String> installed, boolean fee, CapabilityReply reply, Turn turn) {
        Round round = program(installed, fee, reply);
        round.run(turn);
        return round.ledger();
    }

    /** 装没装和费用在组成时定死。交回结果从能力边界读取。 */
    private static Round program(Set<String> installed, boolean fee, CapabilityReply reply) {
        return new Round(installed, fee, name -> reply);
    }

    /** 三字段已通过脏单闸的主轮。 */
    private static Turn opened(List<String> calls) {
        return opened(calls, "");
    }

    /** 三字段已通过脏单闸，并可带一句模型正文。 */
    private static Turn opened(List<String> calls, String utterance) {
        return turn(Entry.main, false, false, false, GOAL, LIMIT, DONE_WHEN, calls, utterance);
    }

    /** 组装一轮输入。 */
    private static Turn turn(
            Entry entry,
            boolean pureChat,
            boolean stopHit,
            boolean askModel,
            Field goal,
            Field limit,
            Field doneWhen,
            List<String> calls,
            String utterance) {
        return new Turn(entry, pureChat, stopHit, askModel, goal, limit, doneWhen, calls, utterance);
    }

    /** 生成互不相同的未装名字。 */
    private static List<String> names(int count) {
        List<String> names = new ArrayList<>();
        for (int i = 1; i <= count; i = i + 1) {
            names.add("工具" + i);
        }
        return names;
    }

    /** 这一件上的编号。 */
    private static String pieceId(List<LedgerEntry> rows) {
        return rows.stream()
                .filter(row -> LedgerEntry.PIECE.equals(row.kind()))
                .map(LedgerEntry::goalId)
                .findFirst()
                .orElseThrow();
    }

    /** 账上有没有这一类。 */
    private static boolean has(List<LedgerEntry> rows, String kind) {
        return rows.stream().anyMatch(row -> kind.equals(row.kind()));
    }

    /** 有没有这个拒绝码。 */
    private static boolean code(List<LedgerEntry> rows, String refusal) {
        return rows.stream().anyMatch(row -> refusal.equals(row.refusalCode()));
    }

    /** 有没有放过这个能力名。 */
    private static boolean named(List<LedgerEntry> rows, String capability) {
        return rows.stream().anyMatch(row -> capability.equals(row.capability()));
    }

    /** 每笔的写入者必须对得上种类。 */
    private static void checkWriters(List<LedgerEntry> rows) {
        for (LedgerEntry row : rows) {
            Writer expected = switch (row.kind()) {
                case LedgerEntry.PIECE -> Writer.base;
                case LedgerEntry.DONE, LedgerEntry.NOT_DONE -> Writer.judge;
                default -> Writer.gate;
            };
            assertEquals(expected, row.writer());
        }
    }
}
