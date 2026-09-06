package com.tepeu.os.policy;

import com.tepeu.os.policy.local.PolicyRulesFile;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyRulesFileTest {

    private final TurnContext ctx = new TurnContext(
            Principal.personal(new PrincipalId("p")),
            Namespace.ofWorkspace(new WorkspaceId("w")),
            new SessionId("s"),
            Optional.empty());

    @Test
    void parsesSyscallOverrideAndDenyExtensions() {
        PolicyRulesFile rules = PolicyRulesFile.parse("""
                execution.fs.write ALLOW
                deny-path secrets/
                deny-command nc -e
                """);
        assertEquals(PolicyVerdict.ALLOW, rules.composePolicy().evaluate(ctx,
                new Syscall("execution.fs.write", Map.of("path", "x.txt"))));
        assertEquals(PolicyVerdict.DENY, rules.composePolicy().evaluate(ctx,
                new Syscall("execution.fs.read", Map.of("path", "secrets/key"))));
        assertEquals(PolicyVerdict.DENY, rules.composePolicy().evaluate(ctx,
                new Syscall("execution.proc.spawn", Map.of("path", "run.sh", "args", "nc -e bash"))));
    }

    @Test
    void builtinsIncludeDefaults() {
        PolicyRulesFile rules = PolicyRulesFile.builtins();
        assertTrue(rules.denyPaths().contains(".env"));
        assertTrue(rules.denyCommands().contains("curl "));
    }
}
