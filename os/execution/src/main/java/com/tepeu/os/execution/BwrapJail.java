package com.tepeu.os.execution;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Linux bubblewrap：能找到 {@code bwrap} 才算可用。无 landlock → 隔离仍是 partial。 */
final class BwrapJail {

    private static final String[] HOST_RO = {"/usr", "/bin", "/lib", "/lib64"};

    private BwrapJail() {
    }

    static boolean available() {
        try {
            Process probe = new ProcessBuilder("bwrap", "--version")
                    .redirectErrorStream(true)
                    .start();
            if (!probe.waitFor(3, TimeUnit.SECONDS)) {
                probe.destroyForcibly();
                return false;
            }
            return probe.exitValue() == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    static List<String> command(Path workspace, Path exe, List<String> extraArgs) {
        Path root = workspace.toAbsolutePath().normalize();
        String rel = root.relativize(exe.toAbsolutePath().normalize()).toString().replace('\\', '/');
        List<String> cmd = new ArrayList<>();
        cmd.add("bwrap");
        cmd.add("--die-with-parent");
        cmd.add("--unshare-net");
        cmd.add("--clearenv");
        cmd.add("--setenv");
        cmd.add("PATH");
        cmd.add("/usr/bin:/bin");
        cmd.add("--setenv");
        cmd.add("HOME");
        cmd.add("/workspace");
        cmd.add("--setenv");
        cmd.add("TMPDIR");
        cmd.add("/workspace");
        for (String host : HOST_RO) {
            cmd.add("--ro-bind-try");
            cmd.add(host);
            cmd.add(host);
        }
        cmd.add("--proc");
        cmd.add("/proc");
        cmd.add("--dev");
        cmd.add("/dev");
        cmd.add("--bind");
        cmd.add(root.toString());
        cmd.add("/workspace");
        cmd.add("--chdir");
        cmd.add("/workspace");
        cmd.add("/workspace/" + rel);
        cmd.addAll(extraArgs);
        return cmd;
    }
}
