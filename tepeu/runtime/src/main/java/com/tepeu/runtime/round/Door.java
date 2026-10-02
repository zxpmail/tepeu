package com.tepeu.runtime.round;

import com.tepeu.runtime.answer.Consent;
import com.tepeu.runtime.answer.Fee;
import com.tepeu.runtime.answer.Fields;
import com.tepeu.runtime.answer.Range;
import com.tepeu.runtime.answer.Stop;
import com.tepeu.runtime.gate.Gate;
import com.tepeu.runtime.judge.Judge;
import com.tepeu.runtime.ledger.Ledger;
import com.tepeu.runtime.ledger.Step;
import com.tepeu.runtime.view.Memory;
import com.tepeu.runtime.view.View;
import com.tepeu.runtime.work.Job;

import java.util.List;

/**
 * 外面那一道门。启动前交一次，然后每次跑一轮。
 * 没有再交能力的入口。结果只从两本记录读。
 */
public final class Door {

    private final Round round;

    private Door(Round round) {
        this.round = round;
    }

    /** 把各块接上。开始之后不能再加、不能再拿掉。 */
    public static Door open(Stop stop, Fields fields, Range range, Fee fee, Consent consent, List<Job> jobs, Memory memory) {
        Ledger ledger = new Ledger();
        Gate gate = new Gate(ledger, stop, fields, range, fee, consent, jobs);
        View view = new View(ledger, memory);
        Judge judge = new Judge(ledger);
        return new Door(new Round(ledger, gate, judge, view));
    }

    /** 跑这一次。调用本身不交回做完。 */
    public void run(Turn turn) {
        round.run(turn);
    }

    /** 读步骤记录。 */
    public List<Step> ledger() {
        return round.ledger();
    }

    /** 读过程记录。 */
    public List<ProcessNote> processLog() {
        return round.processLog();
    }
}
