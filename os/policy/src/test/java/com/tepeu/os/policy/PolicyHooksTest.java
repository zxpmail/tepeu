package com.tepeu.os.policy;

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

class PolicyHooksTest {

    @Test
    void mergeDenyBeatsAskAndAllow() {
        assertEquals(PolicyVerdict.DENY, PolicyHooks.merge(PolicyVerdict.DENY, PolicyVerdict.ALLOW));
        assertEquals(PolicyVerdict.DENY, PolicyHooks.merge(PolicyVerdict.NEED_APPROVAL, PolicyVerdict.DENY));
        assertEquals(PolicyVerdict.NEED_APPROVAL, PolicyHooks.merge(PolicyVerdict.NEED_APPROVAL, PolicyVerdict.ALLOW));
        assertEquals(PolicyVerdict.ALLOW, PolicyHooks.merge(PolicyVerdict.ALLOW, PolicyVerdict.ALLOW));
    }

    @Test
    void composeAppliesBothHooks() {
        PolicyHook hook = PolicyHooks.compose(
                (ctx, call) -> PolicyVerdict.NEED_APPROVAL,
                (ctx, call) -> PolicyVerdict.ALLOW);
        TurnContext ctx = new TurnContext(
                Principal.personal(new PrincipalId("p")),
                Namespace.ofWorkspace(new WorkspaceId("w")),
                new SessionId("s"),
                Optional.empty());
        assertEquals(PolicyVerdict.NEED_APPROVAL, hook.evaluate(ctx, new Syscall("echo", Map.of())));
    }
}
