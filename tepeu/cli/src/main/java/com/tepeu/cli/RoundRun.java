package com.tepeu.cli;

import com.tepeu.runtime.answer.Consent;
import com.tepeu.runtime.answer.Fee;
import com.tepeu.runtime.answer.Fields;
import com.tepeu.runtime.answer.Range;
import com.tepeu.runtime.answer.Stop;
import com.tepeu.runtime.ledger.Step;
import com.tepeu.runtime.round.Call;
import com.tepeu.runtime.round.Door;
import com.tepeu.runtime.round.ProcessNote;
import com.tepeu.runtime.round.Turn;
import com.tepeu.runtime.word.Ability;
import com.tepeu.runtime.word.Entry;
import com.tepeu.runtime.word.Field;
import com.tepeu.runtime.word.Kind;
import com.tepeu.runtime.word.Refusal;
import com.tepeu.runtime.word.ResultKind;
import com.tepeu.runtime.word.Writer;
import com.tepeu.runtime.work.Job;

import java.nio.file.Path;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 跑一轮，把两本记录打成字。
 * 不认识 Spring。命令行启动之后来调用。
 */
public final class RoundRun {

    private RoundRun() {
    }

    /** 这一次对话最多问几次。问一次算 1，不能永远够。 */
    static final int SESSION_ASKS = 32;

    /** 正题一轮里问模型最多扣这么多次。和最多放行 8 次对齐。 */
    static final int ONCE_ASKS = 8;

    /** 人没另写怎样算做完时用这一句。对不上失败代码，所以失败不能靠它做成。 */
    static final String DONE_WHEN = "写或跑成功";

    /** 参数不够时给人看的用法。 */
    public static String usage() {
        return """
                不带参数：停在这里，你说一句，我就去做。说「停」或 stop 就结束。
                用法：tepeu-cli
                三个参数只跑一轮正题：tepeu-cli <要做什么> <限制> <怎样算做完>
                例：tepeu-cli 写一句话 这里 看见回答
                正题可以读、查找、改、写、跑。改、写和跑要在屏幕上输入「同意」或「拒绝」。
                问模型从本机 cc-switch 的 Xiaomi MiMo 读取。
                """;
    }

    /** 停在这里。人说一句就开工。范围是启动时所在的目录。说停就结束。 */
    public static void talk(BufferedReader in, PrintStream out, List<Job> jobs, int times) throws IOException {
        String here = Path.of("").toAbsolutePath().normalize().toString();
        Door door = Door.open(
                new Stop(null),
                new Fields(),
                new Range(),
                new Fee(times),
                new Consent((name, content) -> askPerson(in, out, name, content)),
                jobs,
                null);
        out.println("说一句，我就去做。说「停」或 stop 就结束。这一次最多问 " + times + " 次。");
        while (true) {
            out.print("> ");
            out.flush();
            String line = in.readLine();
            if (line == null) {
                break;
            }
            if (line.isBlank()) {
                continue;
            }
            int before = door.ledger().size();
            door.run(new Turn(
                    Entry.main,
                    false,
                    false,
                    line,
                    false,
                    Field.of(line),
                    Field.of(here),
                    Field.of(DONE_WHEN),
                    List.of(),
                    ""));
            if (stopped(door)) {
                out.println("停了。");
                break;
            }
            out.println(answerOf(door.ledger(), before));
        }
    }

    /** 这一轮是不是人说了停。 */
    private static boolean stopped(Door door) {
        for (ProcessNote note : door.processLog()) {
            if ("主轮停".equals(note.detail())) {
                return true;
            }
        }
        return false;
    }

    /** 这一轮问模型写下的话。没问成就按拦住的原因说一句。工具结果不打在这里。 */
    private static String answerOf(List<Step> steps, int before) {
        StringBuilder said = new StringBuilder();
        String refusal = null;
        String refused = null;
        for (int i = before; i < steps.size(); i++) {
            Step step = steps.get(i);
            if (Kind.RETURN.equals(step.kind()) && Ability.ASK.equals(step.capability())
                    && step.resultKind() == ResultKind.成功
                    && step.body() != null && !step.body().isBlank()) {
                if (said.length() > 0) {
                    said.append(System.lineSeparator());
                }
                said.append(step.body());
            } else if (Kind.REFUSAL.equals(step.kind())) {
                refusal = step.refusalCode();
                refused = step.capability();
            }
        }
        if (said.length() == 0) {
            return refusal == null ? "没有答上来。" : refusalText(refusal, refused);
        }
        if (Refusal.GATE_BLOCKED.equals(refusal) && Ability.ASK.equals(refused)) {
            return said + System.lineSeparator() + "这次不够了，不能再问。";
        }
        return said.toString();
    }

    /** 四种拒绝，各说一句。次数不够只发生在问模型被拦住的时候。 */
    private static String refusalText(String code, String capability) {
        if (Refusal.GATE_BLOCKED.equals(code) && Ability.ASK.equals(capability)) {
            return "这次不够了，不能再问。";
        }
        if (Refusal.GATE_BLOCKED.equals(code) && capability != null) {
            return "这个地方不在允许的范围里。";
        }
        if (Refusal.GATE_BLOCKED.equals(code)) {
            return "这句话没法开工。";
        }
        if (Refusal.NOT_INSTALLED.equals(code)) {
            return "问模型没装上。";
        }
        if (Refusal.USER_DENIED.equals(code)) {
            return "没点头。";
        }
        if (Refusal.APPROVAL_TIMEOUT.equals(code)) {
            return "等太久了。";
        }
        return "没问成。";
    }

    /** 正题跑一轮。不看名单，自己问模型。写和跑要人点头。 */
    public static String once(String goal, String limit, String doneWhen, List<Job> jobs) {
        Door door = Door.open(
                new Stop(null),
                new Fields(),
                new Range(),
                new Fee(ONCE_ASKS),
                new Consent(RoundRun::askPerson),
                jobs,
                null);
        door.run(new Turn(
                Entry.main,
                false,
                false,
                "",
                false,
                Field.of(goal),
                Field.of(limit),
                Field.of(doneWhen),
                List.of(Call.of(Ability.ASK)),
                ""));
        StringBuilder out = new StringBuilder();
        out.append("步骤记录").append(System.lineSeparator());
        for (Step step : door.ledger()) {
            out.append(line(step)).append(System.lineSeparator());
        }
        out.append("过程记录").append(System.lineSeparator());
        for (ProcessNote note : door.processLog()) {
            out.append(note.step());
            if (note.detail() != null && !note.detail().isBlank()) {
                out.append(' ').append(note.detail());
            }
            out.append(System.lineSeparator());
        }
        return out.toString();
    }

    /** 只有整行「同意」才点头。没输入是没结果，别的字是拒绝。 */
    static Consent.Answer hear(String line) {
        if (line == null) {
            return Consent.Answer.没有结果;
        }
        if ("同意".equals(line.strip())) {
            return Consent.Answer.同意;
        }
        return Consent.Answer.拒绝;
    }

    /** 在屏幕上问这一次。超过点头的时限，记没结果。 */
    private static Consent.Answer askPerson(String name, String content) {
        return askPerson(null, System.out, name, content);
    }

    /** 用这一轮的键盘问。没交键盘就用系统输入。 */
    private static Consent.Answer askPerson(BufferedReader in, PrintStream out, String name, String content) {
        PrintStream screen = out == null ? System.out : out;
        screen.println("要「" + name + "」吗？");
        screen.println(content);
        screen.println("输入「同意」或「拒绝」。");
        screen.flush();
        ExecutorService pool = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "consent");
            thread.setDaemon(true);
            return thread;
        });
        try {
            String line = pool.submit(() -> {
                if (in != null) {
                    return in.readLine();
                }
                return new BufferedReader(new InputStreamReader(System.in, System.out.charset())).readLine();
            }).get(Consent.WAIT.toSeconds(), TimeUnit.SECONDS);
            return hear(line);
        } catch (TimeoutException ex) {
            return Consent.Answer.没有结果;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Consent.Answer.没有结果;
        } catch (ExecutionException ex) {
            throw new IllegalStateException("点头没有问成", ex.getCause());
        } finally {
            pool.shutdownNow();
        }
    }

    /** 一笔步骤记录，空的格子不写。 */
    private static String line(Step step) {
        StringJoiner joiner = new StringJoiner(" ");
        joiner.add(step.kind());
        joiner.add(writerName(step.writer()));
        add(joiner, step.goalId());
        add(joiner, step.capability());
        add(joiner, step.refusalCode());
        if (step.resultKind() != null) {
            joiner.add(step.resultKind().name());
        }
        add(joiner, step.resultCode());
        add(joiner, step.body());
        return joiner.toString();
    }

    /** 写入者用中文。 */
    private static String writerName(Writer writer) {
        return switch (writer) {
            case base -> "账";
            case gate -> "检查口";
            case judge -> "判断";
        };
    }

    /** 有字才放上。 */
    private static void add(StringJoiner joiner, String value) {
        if (value != null && !value.isBlank()) {
            joiner.add(value);
        }
    }
}
