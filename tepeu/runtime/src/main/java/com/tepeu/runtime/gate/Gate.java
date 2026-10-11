package com.tepeu.runtime.gate;

import com.tepeu.runtime.answer.Consent;
import com.tepeu.runtime.answer.Fee;
import com.tepeu.runtime.answer.Fields;
import com.tepeu.runtime.answer.Range;
import com.tepeu.runtime.answer.Stop;
import com.tepeu.runtime.ledger.Ledger;
import com.tepeu.runtime.ledger.Step;
import com.tepeu.runtime.word.Ability;
import com.tepeu.runtime.word.Field;
import com.tepeu.runtime.word.Kind;
import com.tepeu.runtime.word.Refusal;
import com.tepeu.runtime.word.Reply;
import com.tepeu.runtime.word.ResultKind;
import com.tepeu.runtime.word.Writer;
import com.tepeu.runtime.work.Attempt;
import com.tepeu.runtime.work.Job;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 检查口。只问、只落笔。
 * 写不写入正题由一轮交来。它不知道正题、只聊天、旁边问，也不数 8 次。
 */
public final class Gate {

    private final Ledger ledger;
    private final Stop stop;
    private final Fields fields;
    private final Range range;
    private final Fee fee;
    private final Consent consent;
    private final Map<String, Job> jobs;

    /** 启动时交进来的回答和做事。同一项不能装两次。 */
    public Gate(Ledger ledger, Stop stop, Fields fields, Range range, Fee fee, Consent consent, List<Job> jobs) {
        this.ledger = ledger;
        this.stop = stop;
        this.fields = fields;
        this.range = range;
        this.fee = fee;
        this.consent = consent;
        Map<String, Job> map = new LinkedHashMap<>();
        if (jobs != null) {
            for (Job job : jobs) {
                if (job == null || job.name() == null || job.name().isBlank()) {
                    throw new IllegalArgumentException("能力必须有名字");
                }
                if (map.containsKey(job.name())) {
                    throw new IllegalArgumentException("同一项能力装了两次：" + job.name());
                }
                map.put(job.name(), job);
            }
        }
        this.jobs = Map.copyOf(map);
    }

    /** 去问停止。它说停，并且这一次要写入正题，才记下停。没装停止就不认停。 */
    public boolean stopped(boolean stopHit, String said, boolean writeDown) {
        if (stop == null || !stop.hit(stopHit, said)) {
            return false;
        }
        if (writeDown) {
            ledger.append(row(Kind.STOP, null, null, null, null, null, null));
        }
        return true;
    }

    /** 去问字段。缺了记待补全，不合格记拦住。没装就不开始做。 */
    public boolean openable(Field goal, Field limit, Field doneWhen) {
        if (fields == null) {
            ledger.append(row(Kind.REFUSAL, Refusal.GATE_BLOCKED, null, null, null, null, null));
            return false;
        }
        Fields.Answer answer = fields.look(goal, limit, doneWhen);
        if (answer == Fields.Answer.还没填齐) {
            ledger.append(row(Kind.AWAITING, null, null, null, null, null, null));
            return false;
        }
        if (answer == Fields.Answer.不合格) {
            ledger.append(row(Kind.REFUSAL, Refusal.GATE_BLOCKED, null, null, null, null, null));
            return false;
        }
        return true;
    }

    /**
     * 放行。顺序是没装就拦，读写真跑先问范围，写和跑再问点头，问模型再问费用。
     * 没通过就不调用。传了任务编号才抄进能力交回。
     */
    public ReleaseMark release(
            String name,
            String goalId,
            boolean writeDown,
            String target,
            String limit,
            String payload,
            String doneWhen,
            String extra,
            List<String> shown) {
        name = modelName(name);
        Job job = jobs.get(name);
        if (job == null) {
            writeRefusal(writeDown, Refusal.NOT_INSTALLED, name, goalId);
            return new ReleaseMark(name, Refusal.NOT_INSTALLED);
        }
        if (ranged(name) && !inRange(target, limit)) {
            writeRefusal(writeDown, Refusal.GATE_BLOCKED, name, goalId);
            return new ReleaseMark(name, Refusal.GATE_BLOCKED);
        }
        if (Ability.RUN.equals(name) && !argsAllowed(extra, limit)) {
            writeRefusal(writeDown, Refusal.GATE_BLOCKED, name, goalId);
            return new ReleaseMark(name, Refusal.GATE_BLOCKED);
        }
        if (needsConsent(name)) {
            ReleaseMark blocked = consentBlock(name, payload, limit, doneWhen, target, extra, goalId, writeDown);
            if (blocked != null) {
                return blocked;
            }
        }
        if (Ability.ASK.equals(name)) {
            ReleaseMark blocked = feeBlock(writeDown, goalId);
            if (blocked != null) {
                return blocked;
            }
        }
        Reply reply;
        try {
            String handed = carries(name) ? extra : payload;
            String directory = Ability.RUN.equals(name) ? limit : "";
            reply = job.run(new Attempt(target, handed, shown, directory));
        } catch (RuntimeException ex) {
            reply = new Reply(ResultKind.失败, null, "");
        }
        if (reply == null || reply.kind() == null) {
            reply = new Reply(ResultKind.失败, null, "");
        } else if (Ability.ASK.equals(name) && reply.kind() == ResultKind.成功
                && reply.body().isBlank() && reply.asks().isEmpty()) {
            reply = new Reply(ResultKind.失败, null, "");
        }
        if (writeDown) {
            ledger.append(row(
                    Kind.RETURN,
                    null,
                    name,
                    goalId,
                    reply.kind(),
                    reply.resultCode(),
                    reply.body()));
        }
        return new ReleaseMark(name, outcomeOf(reply), reply.asks());
    }

    /** 没有执行登记，不能写已安排。 */
    public boolean arrange(boolean writeDown) {
        boolean marked = ledger.read().stream().anyMatch(step -> Kind.ARRANGED_MARK.equals(step.kind()) && step.writer() == Writer.gate);
        if (!marked || !writeDown) {
            return false;
        }
        ledger.append(row(Kind.ARRANGED, null, null, null, null, null, null));
        return true;
    }

    /** 带路径的参数必须落在限制里。普通参数不挡。 */
    private boolean argsAllowed(String extra, String limit) {
        if (extra == null || extra.isBlank()) {
            return true;
        }
        for (String line : extra.split("\\R", -1)) {
            if (line.isBlank()) {
                continue;
            }
            if (pathLike(line) && !inRange(line, limit)) {
                return false;
            }
        }
        return true;
    }

    /** 绝对路径、带目录符、或带上级目录的，都当成路径。单独的 /c 这种开关不是路径。 */
    private static boolean pathLike(String arg) {
        if (arg.contains("..") || arg.contains("\\") || Path.of(arg).isAbsolute()) {
            return true;
        }
        if (!arg.contains("/")) {
            return false;
        }
        return !(arg.startsWith("/") && arg.indexOf('/', 1) < 0 && arg.indexOf('.') < 0 && !arg.contains(" "));
    }

    /** 去问范围。没装，或它说不在里面，都不算通过。 */
    private boolean inRange(String target, String limit) {
        return range != null && range.allows(target, limit);
    }

    /** 去问费用。没装，或这一次不够，都拦住。 */
    private ReleaseMark feeBlock(boolean writeDown, String goalId) {
        if (fee == null || !fee.enough()) {
            writeRefusal(writeDown, Refusal.GATE_BLOCKED, Ability.ASK, goalId);
            return new ReleaseMark(Ability.ASK, Refusal.GATE_BLOCKED);
        }
        return null;
    }

    /** 模型叫 Read、Write。账上仍记读东西、写东西。 */
    private static String modelName(String name) {
        if ("Read".equals(name)) {
            return Ability.READ;
        }
        if ("Write".equals(name)) {
            return Ability.WRITE;
        }
        return name == null ? "" : name;
    }

    /** 写、改、跑、查找都把模型交来的字交给做事，不用人的那一句。 */
    private static boolean carries(String name) {
        return Ability.WRITE.equals(name) || Ability.EDIT.equals(name) || Ability.RUN.equals(name)
                || Ability.GREP.equals(name) || Ability.GLOB.equals(name);
    }

    /** 读、写、改、查找、跑要先问范围。 */
    private static boolean ranged(String name) {
        return Ability.READ.equals(name) || Ability.WRITE.equals(name) || Ability.EDIT.equals(name)
                || Ability.GREP.equals(name) || Ability.GLOB.equals(name) || Ability.RUN.equals(name);
    }

    /** 写、改和跑要人点头。读和查找不用。 */
    private static boolean needsConsent(String name) {
        return Ability.WRITE.equals(name) || Ability.EDIT.equals(name) || Ability.RUN.equals(name);
    }

    /** 去问点头。没通过就拦住。出错记程序拦住，不算超时。 */
    private ReleaseMark consentBlock(
            String name,
            String payload,
            String limit,
            String doneWhen,
            String target,
            String extra,
            String goalId,
            boolean writeDown) {
        if (consent == null) {
            writeRefusal(writeDown, Refusal.GATE_BLOCKED, name, goalId);
            return new ReleaseMark(name, Refusal.GATE_BLOCKED);
        }
        Consent.Answer answer;
        try {
            answer = consent.answer(name, content(name, payload, limit, doneWhen, target, extra));
        } catch (RuntimeException ex) {
            writeRefusal(writeDown, Refusal.GATE_BLOCKED, name, goalId);
            return new ReleaseMark(name, Refusal.GATE_BLOCKED);
        }
        if (answer == Consent.Answer.同意) {
            return null;
        }
        if (answer == Consent.Answer.拒绝) {
            writeRefusal(writeDown, Refusal.USER_DENIED, name, goalId);
            return new ReleaseMark(name, Refusal.USER_DENIED);
        }
        writeRefusal(writeDown, Refusal.APPROVAL_TIMEOUT, name, goalId);
        return new ReleaseMark(name, Refusal.APPROVAL_TIMEOUT);
    }

    /** 这一份内容。要写入的字或参数不同，就不是同一份。 */
    private static String content(String name, String payload, String limit, String doneWhen, String target, String extra) {
        return name + "\n" + (payload == null ? "" : payload) + "\n" + (limit == null ? "" : limit)
                + "\n" + (doneWhen == null ? "" : doneWhen) + "\n" + (target == null ? "" : target)
                + "\n" + (extra == null ? "" : extra);
    }

    /** 过程记录只记结果种类或拒绝代码。 */
    private static String outcomeOf(Reply reply) {
        if (reply.kind() == null) {
            return ResultKind.成功.name();
        }
        return reply.kind().name();
    }

    /** 这一次要写入正题，才把拒绝写上。 */
    private void writeRefusal(boolean writeDown, String code, String name, String goalId) {
        if (!writeDown) {
            return;
        }
        ledger.append(row(Kind.REFUSAL, code, name, goalId, null, null, null));
    }

    /** 这一笔的写入者固定是检查口。 */
    private static Step row(
            String kind,
            String refusalCode,
            String capability,
            String goalId,
            ResultKind resultKind,
            String resultCode,
            String body) {
        return new Step(kind, Writer.gate, refusalCode, capability, goalId, resultKind, resultCode, body, null, null);
    }
}
