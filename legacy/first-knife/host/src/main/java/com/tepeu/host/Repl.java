package com.tepeu.host;

import com.tepeu.load.Assembly.Wired;
import com.tepeu.session.SessionEvent;
import com.tepeu.session.SessionEventType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Objects;

/**
 * 命令行循环。{@code /} 行交 commands 表；其余入收件箱跑一轮（loop.runOnce），
 * 终答只读账印最后一笔 ASSISTANT_MESSAGE——权威状态在事件日志，不在返回值。
 * EOF（Ctrl+Z / Ctrl+D）退出。
 */
public final class Repl {

    private final BufferedReader in;
    private final PrintStream out;
    private final Wired wired;

    public Repl(BufferedReader in, PrintStream out, Wired wired) {
        this.in = Objects.requireNonNull(in, "in");
        this.out = Objects.requireNonNull(out, "out");
        this.wired = Objects.requireNonNull(wired, "wired");
    }

    public void run() throws IOException {
        out.println("Tepeu ｜ 会话 " + wired.session().id().value()
                + " ｜ 工作区 " + wired.workspaceRoot()
                + " ｜ /help 看命令，EOF 退出");
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith("/")) {
                out.println(wired.commands().execute(wired.session(), line));
            } else {
                int before = wired.session().log().readAll().size();
                wired.session().inbox().enqueue(line.trim());
                if (wired.loop().runOnce(wired.session())) {
                    out.println(finalAnswerFrom(before));
                } else {
                    out.println("这轮没跑起来：收件箱领取失败。");
                }
            }
        }
    }

    /** 只认本轮（下标 before 之后）的终答。本轮没有就不回退到旧答复。 */
    private String finalAnswerFrom(int before) {
        List<SessionEvent> all = wired.session().log().readAll();
        for (int i = all.size() - 1; i >= before && i >= 0; i--) {
            if (all.get(i).type() == SessionEventType.ASSISTANT_MESSAGE) {
                return all.get(i).body();
            }
        }
        return "（这轮没有终答。/status 看账。）";
    }
}
