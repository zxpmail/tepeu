package com.tepeu.os.host;

import com.tepeu.os.loop.TurnOutcome;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Scanner;

@Component
@ConditionalOnProperty(name = "tepeu.cli.enabled", havingValue = "true", matchIfMissing = true)
public final class TepeuCliRunner implements ApplicationRunner, ExitCodeGenerator {

    private final CliSession cli;
    private int exitCode;

    TepeuCliRunner(CliSession cli) {
        this.cli = cli;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> positional = args.getNonOptionArgs();
        if (positional.isEmpty()) {
            exitCode = runInteractive();
            return;
        }
        String cmd = positional.get(0);
        if ("chat".equals(cmd)) {
            exitCode = runChat(positional.subList(1, positional.size()));
            return;
        }
        if ("help".equals(cmd)) {
            printHelp();
            exitCode = 0;
            return;
        }
        System.err.println("unknown command: " + cmd);
        printHelp();
        exitCode = 2;
    }

    @Override
    public int getExitCode() {
        return exitCode;
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
        return outcome.completed() || outcome.kind() == TurnOutcome.Kind.EMPTY ? 0 : 1;
    }

    private int runInteractive() {
        System.out.println("Tepeu OS CLI — session=" + cli.sessionId().value());
        System.out.println("Enter message, /command, or :quit");
        try (Scanner scanner = new Scanner(System.in)) {
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
        return 0;
    }

    private static void printHelp() {
        System.out.println("Tepeu OS host (CLI)");
        System.out.println("  (no args)     interactive REPL");
        System.out.println("  chat <text>   one-shot turn");
        System.out.println("  help          this message");
        System.out.println();
        System.out.println("REPL: plain text → Loop; /help /approve → Slash; :session :quit");
    }
}
