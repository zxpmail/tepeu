package com.tepeu.os.orchestration.conformance;

import com.tepeu.os.conformance.ConformanceCase;
import com.tepeu.os.identity.Namespace;
import com.tepeu.os.identity.Principal;
import com.tepeu.os.identity.PrincipalId;
import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.identity.WorkspaceId;
import com.tepeu.os.orchestration.CommandDispatcher;
import com.tepeu.os.orchestration.CommandHandler;
import com.tepeu.os.orchestration.CommandKind;
import com.tepeu.os.orchestration.CommandResult;
import com.tepeu.os.orchestration.HelpCommand;
import com.tepeu.os.session.Session;
import com.tepeu.os.session.memory.InMemorySessionStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.tepeu.os.conformance.ConformanceCheck.check;
import static com.tepeu.os.conformance.ConformanceCheck.checkEquals;
import static com.tepeu.os.conformance.ConformanceCheck.expectThrows;

/**
 * CommandDispatcher 套件（测试）：local/prompt 两型；未知命令不进模型。不进发行 jar。
 */
public final class CommandConformance {

    private CommandConformance() {
    }

    public static List<ConformanceCase> suite() {
        List<ConformanceCase> cases = new ArrayList<>();
        cases.add(new ConformanceCase("command", "非 / 开头不是命令",
                () -> {
                    check(CommandDispatcher.parse("hello").isEmpty(), "普通文本");
                    check(CommandDispatcher.parse("/").isEmpty(), "裸斜杠");
                    check(CommandDispatcher.parse("  /help").isPresent(), "可 strip");
                }));
        cases.add(new ConformanceCase("command", "/help 是 local，零 Inbox",
                () -> {
                    Fixture f = fixture();
                    CommandResult r = f.dispatcher.dispatch(f.turn, f.session, "/help");
                    check(r.ok(), "应成功");
                    checkEquals(CommandKind.LOCAL, r.kind(), "local");
                    check(r.output().contains("/help"), "列出自己: " + r.output());
                    check(f.session.inbox().claimNext().isEmpty(), "不得 enqueue");
                }));
        cases.add(new ConformanceCase("command", "未知命令失败，不经模型",
                () -> {
                    Fixture f = fixture();
                    CommandResult r = f.dispatcher.dispatch(f.turn, f.session, "/nope");
                    check(!r.ok(), "必须失败");
                    check(r.output().contains("unknown"), "可见: " + r.output());
                    check(f.session.inbox().claimNext().isEmpty(), "不得当 user 进 Inbox");
                }));
        cases.add(new ConformanceCase("command", "prompt 型展开进 Inbox，不跑 Loop",
                () -> {
                    Fixture f = fixture();
                    f.dispatcher.register(new CommandHandler() {
                        @Override
                        public CommandKind kind() {
                            return CommandKind.PROMPT;
                        }

                        @Override
                        public String name() {
                            return "ask";
                        }

                        @Override
                        public String description() {
                            return "expand";
                        }

                        @Override
                        public CommandResult execute(TurnContext ctx, List<String> args) {
                            return CommandResult.prompt("please " + String.join(" ", args));
                        }
                    });
                    CommandResult r = f.dispatcher.dispatch(f.turn, f.session, "/ask foo bar");
                    check(r.ok(), "应成功");
                    checkEquals(CommandKind.PROMPT, r.kind(), "prompt");
                    var lease = f.session.inbox().claimNext().orElseThrow();
                    checkEquals("please foo bar", f.session.inbox().claimed(lease.claimId()).orElseThrow().body(),
                            "展开进 Inbox");
                    checkEquals("command:ask",
                            f.session.inbox().claimed(lease.claimId()).orElseThrow().source().orElse(""),
                            "source");
                }));
        cases.add(new ConformanceCase("command", "注册序确定、重复名拒绝",
                () -> {
                    CommandDispatcher d = new CommandDispatcher();
                    d.register(named("zeta"));
                    d.register(named("alpha"));
                    checkEquals(List.of("alpha", "zeta"), d.registered(), "规范序");
                    expectThrows(IllegalStateException.class, () -> d.register(named("alpha")));
                }));
        return List.copyOf(cases);
    }

    private static CommandHandler named(String name) {
        return new CommandHandler() {
            @Override
            public CommandKind kind() {
                return CommandKind.LOCAL;
            }

            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return name;
            }

            @Override
            public CommandResult execute(TurnContext ctx, List<String> args) {
                return CommandResult.local("ok");
            }
        };
    }

    private static Fixture fixture() {
        InMemorySessionStore store = new InMemorySessionStore();
        Principal owner = Principal.personal(new PrincipalId("cmd-user"));
        Namespace ns = Namespace.ofWorkspace(new WorkspaceId("cmd-ws"));
        Session session = store.create(owner, ns, Optional.empty());
        TurnContext turn = new TurnContext(owner, ns, session.id(), Optional.empty());
        CommandDispatcher dispatcher = new CommandDispatcher();
        dispatcher.register(new HelpCommand(dispatcher));
        return new Fixture(session, turn, dispatcher);
    }

    private record Fixture(Session session, TurnContext turn, CommandDispatcher dispatcher) {
    }
}
