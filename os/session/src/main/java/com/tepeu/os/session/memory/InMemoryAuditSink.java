package com.tepeu.os.session.memory;

import com.tepeu.os.session.AuditRecord;
import com.tepeu.os.session.AuditSink;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 单机内存 AuditSink — conformance / 测试；发行默认随持久化插头。 */
public final class InMemoryAuditSink implements AuditSink {

    private final Clock clock;
    private final List<AuditRecord> records = new ArrayList<>();

    public InMemoryAuditSink() {
        this(Clock.systemUTC());
    }

    public InMemoryAuditSink(Clock clock) {
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public synchronized long record(String actor, String action, String detail, Map<String, String> attrs) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(detail, "detail");
        long seq = records.size() + 1;
        records.add(new AuditRecord(seq, clock.instant(), actor, action, detail, attrs));
        return seq;
    }

    @Override
    public synchronized List<AuditRecord> readAll() {
        return List.copyOf(records);
    }
}
