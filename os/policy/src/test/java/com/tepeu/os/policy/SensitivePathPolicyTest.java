package com.tepeu.os.policy;

import com.tepeu.os.policy.local.DefaultRuleMatrix;
import com.tepeu.os.policy.local.SensitivePathPolicy;
import com.tepeu.os.policy.local.PolicyHooks;
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

class SensitivePathPolicyTest {

    private final SensitivePathPolicy policy = SensitivePathPolicy.defaults();
    private final TurnContext ctx = new TurnContext(
            Principal.personal(new PrincipalId("p")),
            Namespace.ofWorkspace(new WorkspaceId("w")),
            new SessionId("s"),
            Optional.empty());

    @Test
    void deniesSensitivePathsEvenWhenMatrixWouldAsk() {
        PolicyHook stack = PolicyHooks.compose(new DefaultRuleMatrix(), policy);
        assertEquals(PolicyVerdict.DENY, stack.evaluate(ctx,
                new Syscall("execution.fs.write", Map.of("path", ".env"))));
        assertEquals(PolicyVerdict.DENY, stack.evaluate(ctx,
                new Syscall("execution.fs.read", Map.of("path", "secrets/.ssh/id_rsa"))));
        assertEquals(PolicyVerdict.DENY, stack.evaluate(ctx,
                new Syscall("execution.fs.write", Map.of("path", ".git/config"))));
    }

    @Test
    void allowsBenignPaths() {
        PolicyHook stack = PolicyHooks.compose(new DefaultRuleMatrix(), policy);
        assertEquals(PolicyVerdict.NEED_APPROVAL, stack.evaluate(ctx,
                new Syscall("execution.fs.write", Map.of("path", "src/App.java"))));
        assertEquals(PolicyVerdict.ALLOW, stack.evaluate(ctx,
                new Syscall("execution.fs.read", Map.of("path", "README.md"))));
    }

    @Test
    void ignoresNonFsSyscalls() {
        assertEquals(PolicyVerdict.ALLOW, policy.evaluate(ctx,
                new Syscall("llm.generate", Map.of("model", "m"))));
    }
}
