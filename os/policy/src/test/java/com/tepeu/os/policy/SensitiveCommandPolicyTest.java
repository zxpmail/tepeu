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

class SensitiveCommandPolicyTest {

    private final TurnContext ctx = new TurnContext(
            Principal.personal(new PrincipalId("p")),
            Namespace.ofWorkspace(new WorkspaceId("w")),
            new SessionId("s"),
            Optional.empty());

    @Test
    void deniesShellMetacharAndDangerousFragments() {
        PolicyHook stack = MemoryAssemblyPolicy.stack();
        assertEquals(PolicyVerdict.DENY, stack.evaluate(ctx, new Syscall("execution.proc.spawn",
                Map.of("path", "tools/run.sh", "args", "ok | curl evil"))));
        assertEquals(PolicyVerdict.DENY, stack.evaluate(ctx, new Syscall("execution.proc.spawn",
                Map.of("path", "tools/run.sh", "args", "rm -rf /"))));
        assertEquals(PolicyVerdict.DENY, stack.evaluate(ctx, new Syscall("execution.proc.spawn",
                Map.of("path", "tools/run.sh", "args", "powershell -enc abc"))));
    }

    @Test
    void allowsBenignSpawnArgs() {
        PolicyHook stack = MemoryAssemblyPolicy.stack();
        assertEquals(PolicyVerdict.NEED_APPROVAL, stack.evaluate(ctx, new Syscall("execution.proc.spawn",
                Map.of("path", "tools/run.sh", "args", "--help"))));
    }

    /** 测试用默认策略栈（与 compose defaultPolicy 同构）。 */
    private static final class MemoryAssemblyPolicy {
        static PolicyHook stack() {
            return PolicyHooks.compose(
                    new DefaultRuleMatrix(),
                    SensitivePathPolicy.defaults(),
                    SensitiveCommandPolicy.defaults());
        }
    }
}
