package com.tepeu.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.tepeu.identity.InvokeContext;
import com.tepeu.identity.Principal;
import com.tepeu.identity.PrincipalId;
import com.tepeu.identity.SessionId;
import com.tepeu.identity.WorkspaceId;
import com.tepeu.policy.local.DefaultRuleMatrix;
import com.tepeu.syscall.Syscall;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PolicyTest {

    private final InvokeContext ctx = new InvokeContext(
            new Principal(new PrincipalId("u")),
            new WorkspaceId("ws"),
            new SessionId("default"));

    @Test
    void defaultMatrixFirstCut() {
        DefaultRuleMatrix matrix = new DefaultRuleMatrix();
        assertEquals(PolicyVerdict.ALLOW,
                matrix.evaluate(ctx, new Syscall("llm.generate", Map.of())));
        assertEquals(PolicyVerdict.ALLOW,
                matrix.evaluate(ctx, new Syscall("execution.fs.read", Map.of("path", "a.txt"))));
        assertEquals(PolicyVerdict.ALLOW,
                matrix.evaluate(ctx, new Syscall("execution.sandbox.probe", Map.of())));
        assertEquals(PolicyVerdict.NEED_APPROVAL,
                matrix.evaluate(ctx, new Syscall("execution.fs.write", Map.of("path", "a.txt"))));
        assertEquals(PolicyVerdict.NEED_APPROVAL,
                matrix.evaluate(ctx, new Syscall("execution.proc.spawn", Map.of("path", "run.sh"))));
        assertEquals(PolicyVerdict.DENY,
                matrix.evaluate(ctx, new Syscall("execution.fs.delete", Map.of("path", "a.txt"))));
        assertEquals(PolicyVerdict.DENY,
                matrix.evaluate(ctx, new Syscall("unknown.op", Map.of())));
    }

    @Test
    void overrideWinsAndParseReadsRulesText() {
        DefaultRuleMatrix matrix = DefaultRuleMatrix.parse("""
                # policy.rules
                llm.generate DENY

                execution.fs.write ALLOW
                """);
        assertEquals(PolicyVerdict.DENY,
                matrix.evaluate(ctx, new Syscall("llm.generate", Map.of())));
        assertEquals(PolicyVerdict.ALLOW,
                matrix.evaluate(ctx, new Syscall("execution.fs.write", Map.of("path", "a.txt"))));
        assertEquals(PolicyVerdict.NEED_APPROVAL,
                matrix.evaluate(ctx, new Syscall("execution.proc.spawn", Map.of())));
        assertEquals(PolicyVerdict.DENY,
                matrix.evaluate(ctx, new Syscall("unknown.op", Map.of())));
        assertThrows(IllegalArgumentException.class, () -> DefaultRuleMatrix.parse("llm.generate"));
        assertThrows(IllegalArgumentException.class, () -> DefaultRuleMatrix.parse("llm.generate MAYBE"));
        assertEquals(PolicyVerdict.ALLOW,
                DefaultRuleMatrix.parse("   ").evaluate(ctx, new Syscall("llm.generate", Map.of())));
    }

    @Test
    void evaluateRejectsNull() {
        DefaultRuleMatrix matrix = new DefaultRuleMatrix();
        assertThrows(NullPointerException.class,
                () -> matrix.evaluate(null, new Syscall("llm.generate", Map.of())));
        assertThrows(NullPointerException.class, () -> matrix.evaluate(ctx, null));
    }
}
