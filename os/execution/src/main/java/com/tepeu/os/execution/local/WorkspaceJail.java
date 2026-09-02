package com.tepeu.os.execution.local;


import com.sun.jna.platform.win32.Kernel32;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Optional;

/** 相对工作区解析；绝对路径、符号链接、junction 一律 empty。 */
public final class WorkspaceJail {

    static final int FILE_ATTRIBUTE_REPARSE_POINT = 0x400;
    static final int INVALID_FILE_ATTRIBUTES = -1;

    private WorkspaceJail() {
    }

    public static Optional<Path> resolve(Path workspaceRoot, String userPath) {
        if (workspaceRoot == null || userPath == null || userPath.isBlank() || userPath.indexOf('\0') >= 0) {
            return Optional.empty();
        }
        if (absoluteUserPath(userPath)) {
            return Optional.empty();
        }
        Path root;
        Path rootReal;
        try {
            root = workspaceRoot.toAbsolutePath().normalize();
            rootReal = root.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            return Optional.empty();
        }
        Path resolved;
        try {
            resolved = root.resolve(userPath).normalize();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
        if (!resolved.startsWith(root)) {
            return Optional.empty();
        }
        try {
            Path walk = root;
            Path relative = root.relativize(resolved);
            if (!relative.toString().isEmpty() && !".".equals(relative.toString())) {
                for (Path part : relative) {
                    walk = walk.resolve(part);
                    if (Files.exists(walk, LinkOption.NOFOLLOW_LINKS) && isLinkOrJunction(walk)) {
                        return Optional.empty();
                    }
                }
            }
            Path existing = resolved;
            while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
                existing = existing.getParent();
            }
            if (existing == null) {
                return Optional.empty();
            }
            Path existingReal = existing.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (!existingReal.startsWith(rootReal)) {
                return Optional.empty();
            }
        } catch (IOException e) {
            return Optional.empty();
        }
        return Optional.of(resolved);
    }

    static boolean absoluteUserPath(String userPath) {
        String trimmed = userPath.trim();
        if (trimmed.startsWith("/") || trimmed.startsWith("\\")) {
            return true;
        }
        if (trimmed.length() >= 2 && trimmed.charAt(1) == ':') {
            return true;
        }
        try {
            return Path.of(trimmed).isAbsolute();
        } catch (InvalidPathException e) {
            return true;
        }
    }

    static boolean isLinkOrJunction(Path path) {
        if (Files.isSymbolicLink(path)) {
            return true;
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            return false;
        }
        try {
            int attr = Kernel32.INSTANCE.GetFileAttributes(path.toAbsolutePath().toString());
            if (attr == INVALID_FILE_ATTRIBUTES) {
                return false;
            }
            return (attr & FILE_ATTRIBUTE_REPARSE_POINT) != 0;
        } catch (UnsatisfiedLinkError | NoClassDefFoundError e) {
            return false;
        }
    }
}
