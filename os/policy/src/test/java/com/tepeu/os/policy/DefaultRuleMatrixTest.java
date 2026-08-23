package com.tepeu.os.policy;

import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.SessionId;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.syscall.Syscall;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultRuleMatrixTest {

    private final DefaultRuleMatrix matrix = new DefaultRuleMatrix();
    private final TurnContext ctx = new TurnContext(
            Principal.personal(new PrincipalId("p")),
            Namespace.ofWorkspace(new WorkspaceId("w")),
            new SessionId("s"),
            Optional.empty());

    @Test
    void llmAllowWriteAskUnknownDeny() {
        assertEquals(PolicyVerdict.ALLOW, matrix.evaluate(ctx, new Syscall("llm.generate", Map.of())));
        assertEquals(PolicyVerdict.ALLOW, matrix.evaluate(ctx, new Syscall("execution.fs.read", Map.of())));
        assertEquals(PolicyVerdict.ALLOW, matrix.evaluate(ctx, new Syscall("execution.sandbox.probe", Map.of())));
        assertEquals(PolicyVerdict.NEED_APPROVAL, matrix.evaluate(ctx, new Syscall("execution.fs.write", Map.of())));
        assertEquals(PolicyVerdict.NEED_APPROVAL, matrix.evaluate(ctx, new Syscall("execution.proc.spawn", Map.of())));
        assertEquals(PolicyVerdict.DENY, matrix.evaluate(ctx, new Syscall("echo", Map.of())));
        assertEquals(PolicyVerdict.DENY, matrix.evaluate(ctx, new Syscall("execution.unknown", Map.of())));
    }

    @Test
    void parseOverridesExactName() {
        DefaultRuleMatrix parsed = DefaultRuleMatrix.parse("""
                # comment
                execution.fs.write ALLOW
                echo NEED_APPROVAL
                """);
        assertEquals(PolicyVerdict.ALLOW, parsed.evaluate(ctx, new Syscall("execution.fs.write", Map.of())));
        assertEquals(PolicyVerdict.NEED_APPROVAL, parsed.evaluate(ctx, new Syscall("echo", Map.of())));
        assertEquals(PolicyVerdict.ALLOW, parsed.evaluate(ctx, new Syscall("llm.generate", Map.of())));
    }
}
