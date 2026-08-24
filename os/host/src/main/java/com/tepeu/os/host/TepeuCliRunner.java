package com.tepeu.os.host;

import com.tepeu.os.loop.TurnOutcome;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.util.List;
import java.util.Scanner;

@Component
@ConditionalOnProperty(name = "tepeu.cli.enabled", havingValue = "true", matchIfMissing = true)
public final class TepeuCliRunner implements ApplicationRunner {

    private final CliSession cli;

    TepeuCliRunner(CliSession cli) {
        this.cli = cli;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> positional = args.getNonOptionArgs();
        if (positional.isEmpty()) {
            runInteractive();
            return;
        }
        String cmd = positional.get(0);
        if ("chat".equals(cmd)) {
            int code = runChat(positional.subList(1, positional.size()));
            System.exit(code);
        }
        if ("help".equals(cmd)) {
            printHelp();
            return;
        }
        System.err.println("unknown command: " + cmd);
        printHelp();
        System.exit(2);
    }

    private int runChat(List<String> messageParts) {
        if (messageParts.isEmpty()) {
            System.err.println("usage: chat <message>");
            return 2;
        }
        String message = String.join(" ", messageParts);
        TurnOutcome outcome = cli.handleLine(message);
        if (cli.isExit(outcome)) {
            return 0;
        }
        if (outcome == null) {
            return 1;
        }
        return outcome.completed() ? 0 : 1;
    }

    private void runInteractive() {
        System.out.println("Tepeu OS CLI — session=" + cli.sessionId().value());
        System.out.println("Enter message, /command, or :quit");
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("tepeu> ");
            if (!scanner.hasNextLine()) {
                break;
            }
            TurnOutcome outcome = cli.handleLine(scanner.nextLine());
            if (cli.isExit(outcome)) {
                break;
            }
        }
    }

    private static void printHelp() {
        PrintWriter out = new PrintWriter(System.out, true);
        out.println("Tepeu OS host (CLI)");
        out.println("  (no args)     interactive REPL");
        out.println("  chat <text>   one-shot turn");
        out.println("  help          this message");
        out.println();
        out.println("REPL: plain text → Loop; /help /approve → Slash; :session :quit");
    }
}
