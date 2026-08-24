package com.tepeu.os.policy;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.syscall.Syscall;

import java.util.Objects;

/**
 * Policy 组合 — deny &gt; ask &gt; allow（底板 §2 卫兵代数同构）。
 */
public final class PolicyHooks {

    private PolicyHooks() {
    }

    public static PolicyHook compose(PolicyHook first, PolicyHook second) {
        return compose(new PolicyHook[] {first, second});
    }

    public static PolicyHook compose(PolicyHook... hooks) {
        Objects.requireNonNull(hooks, "hooks");
        if (hooks.length == 0) {
            throw new IllegalArgumentException("hooks empty");
        }
        for (PolicyHook hook : hooks) {
            Objects.requireNonNull(hook, "hook");
        }
        return (TurnContext ctx, Syscall syscall) -> {
            PolicyVerdict verdict = PolicyVerdict.ALLOW;
            for (PolicyHook hook : hooks) {
                verdict = merge(verdict, hook.evaluate(ctx, syscall));
            }
            return verdict;
        };
    }

    static PolicyVerdict merge(PolicyVerdict a, PolicyVerdict b) {
        if (a == PolicyVerdict.DENY || b == PolicyVerdict.DENY) {
            return PolicyVerdict.DENY;
        }
        if (a == PolicyVerdict.NEED_APPROVAL || b == PolicyVerdict.NEED_APPROVAL) {
            return PolicyVerdict.NEED_APPROVAL;
        }
        return PolicyVerdict.ALLOW;
    }
}
