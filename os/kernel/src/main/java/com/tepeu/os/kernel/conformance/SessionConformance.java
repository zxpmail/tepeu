package com.tepeu.os.kernel.conformance;

import com.tepeu.os.kernel.bus.Usage;
import com.tepeu.os.kernel.session.ClaimLease;
import com.tepeu.os.kernel.session.Session;
import com.tepeu.os.kernel.session.SessionEvent;
import com.tepeu.os.kernel.session.SessionEventType;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tepeu.os.kernel.conformance.ConformanceCheck.check;
import static com.tepeu.os.kernel.conformance.ConformanceCheck.checkEquals;
import static com.tepeu.os.kernel.conformance.ConformanceCheck.expectThrows;

/**
 * Session 端口 conformance 套件 — 任何 SessionStore 实现必须全绿。
 * 契约来源：底板 §4-3（三 store）/ §9（事件日志立规）/ ADR-016 第四·五·六·七·八轮。
 * 工厂必须接受可注入时钟（TTL/超时可测试性）。
 */
public final class SessionConformance {

    /** 与实现约定的默认租约 TTL（第七轮：死租约可回收）。 */
    public static final Duration LEASE_TTL = Duration.ofSeconds(300);

    public interface SessionFactory {
        Session newSession(Clock clock);
    }

    private SessionConformance() {
    }

    public static List<ConformanceCase> suite(SessionFactory factory) {
        List<ConformanceCase> cases = new ArrayList<>();

        // ===== entries（事实日志）=====
        cases.add(new ConformanceCase("log", "seq 由 append 点分配：从 1 起、单调、等值 length",
                () -> {
                    Session s = factory.newSession(Clock.systemUTC());
                    long s1 = s.log().append(SessionEventType.USER_MESSAGE, "m1", Map.of());
                    long s2 = s.log().append(SessionEventType.ASSISTANT_MESSAGE, "m2", Map.of());
                    long s3 = s.log().append(SessionEventType.TOOL_CALL, "m3", Map.of());
                    check(s1 == 1 && s2 == 2 && s3 == 3,
                            "seq 期望 1,2,3 实得 " + s1 + "," + s2 + "," + s3);
                    check(s.log().readAll().size() == 3, "无种子日志 seq 应等值 log.length");
                }));
        cases.add(new ConformanceCase("log", "readAll 保序、get(seq) 命中",
                () -> {
                    Session s = factory.newSession(Clock.systemUTC());
                    s.log().append(SessionEventType.USER_MESSAGE, "a", Map.of());
                    s.log().append(SessionEventType.USER_MESSAGE, "b", Map.of());
                    List<SessionEvent> all = s.log().readAll();
                    checkEquals("a", all.get(0).body(), "readAll 顺序");
                    checkEquals("b", all.get(1).body(), "readAll 顺序");
                    check(s.log().get(2).map(e -> "b".equals(e.body())).orElse(false),
                            "get(seq) 未命中期望事件");
                    check(s.log().get(99).isEmpty(), "get 不存在 seq 应 empty");
                }));
        cases.add(new ConformanceCase("log", "append 拒绝 null body（lossless 校验在 append 点）",
                () -> {
                    Session s = factory.newSession(Clock.systemUTC());
                    expectThrows(NullPointerException.class,
                            () -> s.log().append(SessionEventType.USER_MESSAGE, null, Map.of()));
                }));

        // ===== Inbox（会话调度）=====
        cases.add(new ConformanceCase("inbox", "claim 按 FIFO 领取，ack 后不再可领，租约带未来 TTL",
                () -> {
                    Session s = factory.newSession(Clock.systemUTC());
                    String m1 = s.inbox().enqueue("one", Optional.of("user"));
                    String m2 = s.inbox().enqueue("two", Optional.of("user"));
                    ClaimLease lease = s.inbox().claimNext()
                            .orElseThrow(() -> new AssertionError("首条应可领取"));
                    checkEquals(m1, lease.messageId(), "FIFO 首条");
                    check(!lease.expiresAt().isBefore(java.time.Instant.now()),
                            "租约必须带未来 expiresAt（TTL）");
                    s.inbox().ack(lease.claimId());
                    ClaimLease second = s.inbox().claimNext()
                            .orElseThrow(() -> new AssertionError("ack 后第二条应可领取"));
                    checkEquals(m2, second.messageId(), "FIFO 第二条");
                }));
        cases.add(new ConformanceCase("inbox", "nack 归还消息，可被再次领取",
                () -> {
                    Session s = factory.newSession(Clock.systemUTC());
                    String id = s.inbox().enqueue("hello", Optional.of("user"));
                    ClaimLease lease = s.inbox().claimNext().orElseThrow();
                    s.inbox().nack(lease.claimId());
                    ClaimLease re = s.inbox().claimNext()
                            .orElseThrow(() -> new AssertionError("nack 后应可再次领取"));
                    checkEquals(id, re.messageId(), "nack 归还的是同一条消息");
                }));
        cases.add(new ConformanceCase("inbox", "优先级：NOW 先于 NEXT/LATER 领取，同级 FIFO",
                () -> {
                    Session s = factory.newSession(Clock.systemUTC());
                    String later = s.inbox().enqueue("later", Optional.of("user"),
                            com.tepeu.os.kernel.session.Priority.LATER);
                    String next = s.inbox().enqueue("next", Optional.of("user"),
                            com.tepeu.os.kernel.session.Priority.NEXT);
                    String now = s.inbox().enqueue("now", Optional.of("user"),
                            com.tepeu.os.kernel.session.Priority.NOW);
                    checkEquals(now, s.inbox().claimNext().orElseThrow().messageId(), "第一条应为 NOW");
                    checkEquals(next, s.inbox().claimNext().orElseThrow().messageId(), "第二条应为 NEXT");
                    checkEquals(later, s.inbox().claimNext().orElseThrow().messageId(), "第三条应为 LATER");
                }));
        cases.add(new ConformanceCase("inbox", "死租约可回收：过期后消息重新可领（第七轮）",
                () -> {
                    MutableClock clock = new MutableClock();
                    Session s = factory.newSession(clock);
                    String id = s.inbox().enqueue("sticky", Optional.of("user"));
                    ClaimLease lease = s.inbox().claimNext().orElseThrow();
                    checkEquals(id, lease.messageId(), "首领");
                    clock.advance(LEASE_TTL.plusSeconds(1)); // 持有者消失，拨过 TTL
                    ClaimLease revived = s.inbox().claimNext()
                            .orElseThrow(() -> new AssertionError("过期租约应被回收并重新可领"));
                    checkEquals(id, revived.messageId(), "回收的是同一条消息");
                    check(!revived.claimId().equals(lease.claimId()), "回收须发新租约 id");
                }));

        // ===== ledger（用量账本）=====
        cases.add(new ConformanceCase("ledger", "record 分配单调 seq，readAll 保序可派生消耗",
                () -> {
                    Session s = factory.newSession(Clock.systemUTC());
                    long l1 = s.ledger().record("llm.stream", new Usage(10, 5, 0, 0));
                    long l2 = s.ledger().record("llm.stream", new Usage(0, 0, 3, 2));
                    check(l1 == 1 && l2 == 2, "ledger seq 期望 1,2 实得 " + l1 + "," + l2);
                    checkEquals("llm.stream", s.ledger().readAll().get(0).syscallName(), "保序");
                    long totalInput = s.ledger().readAll().stream()
                            .mapToLong(e -> e.usage().totalInput()).sum();
                    check(totalInput == 15, "消耗派生：10+3+2=15 实得 " + totalInput);
                }));
        cases.add(new ConformanceCase("ledger", "record 拒绝 null usage",
                () -> {
                    Session s = factory.newSession(Clock.systemUTC());
                    expectThrows(NullPointerException.class,
                            () -> s.ledger().record("llm.stream", null));
                }));

        // ===== surface 替换代数 =====
        cases.add(new ConformanceCase("replace", "replaceRange 不删审计日志，checkpoint 插区间位，后续 append 落 surface 尾",
                () -> {
                    Session s = factory.newSession(Clock.systemUTC());
                    s.log().append(SessionEventType.USER_MESSAGE, "m1", Map.of());
                    s.log().append(SessionEventType.ASSISTANT_MESSAGE, "m2", Map.of());
                    s.log().append(SessionEventType.USER_MESSAGE, "m3", Map.of());
                    s.logReplace().replaceRange(1, 2, "summary of 1-2");
                    check(s.log().readAll().size() == 4, "底层审计日志须保留全部 + checkpoint");
                    List<SessionEvent> surface = s.logReplace().surface();
                    check(surface.size() == 2, "surface 应为 checkpoint+m3 实得 " + surface.size());
                    checkEquals(SessionEventType.COMPACTION_CHECKPOINT, surface.get(0).type(),
                            "checkpoint 在被替换区间位置");
                    checkEquals("m3", surface.get(1).body(), "区间外事件保留");
                    s.log().append(SessionEventType.USER_MESSAGE, "m4", Map.of());
                    check(s.logReplace().surface().size() == 3, "替换后新事件落 surface 尾");
                }));
        cases.add(new ConformanceCase("replace", "区间校验：from<=0 或 from>to 拒绝",
                () -> {
                    Session s = factory.newSession(Clock.systemUTC());
                    s.log().append(SessionEventType.USER_MESSAGE, "m1", Map.of());
                    expectThrows(IllegalArgumentException.class,
                            () -> s.logReplace().replaceRange(0, 1, "bad"));
                    expectThrows(IllegalArgumentException.class,
                            () -> s.logReplace().replaceRange(2, 1, "bad"));
                }));

        // ===== 词汇表（第六/八轮 manifest 钉死）=====
        cases.add(new ConformanceCase("vocabulary", "事件词汇表钉死 7 类",
                () -> {
                    Set<String> expected = Set.of(
                            "USER_MESSAGE", "ASSISTANT_MESSAGE", "TOOL_CALL", "TOOL_RESULT",
                            "REASONING", "PLAN_STEP", "COMPACTION_CHECKPOINT");
                    Set<String> actual = Arrays.stream(SessionEventType.values())
                            .map(Enum::name)
                            .collect(Collectors.toSet());
                    checkEquals(expected, actual,
                            "词汇表漂移（新增/删除须显式改本用例与 ADR）: 实得 "
                                    + actual.stream().sorted()
                                            .collect(Collectors.joining(","))
                                            .toLowerCase(Locale.ROOT));
                }));

        return List.copyOf(cases);
    }
}
