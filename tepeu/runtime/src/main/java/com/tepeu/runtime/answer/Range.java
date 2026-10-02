package com.tepeu.runtime.answer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 回答目标在不在限制里面。不写步骤记录，也不问点头。
 * 整句相同算在里面。限制是真实目录时，目标必须落在里面。
 */
public final class Range {

    /** 没带目标，或不在里面，就是不在。通配不做。 */
    public boolean allows(String target, String limit) {
        if (target == null || target.isBlank() || limit == null) {
            return false;
        }
        if (target.equals(limit)) {
            return true;
        }
        try {
            Path root = Path.of(limit);
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root)) {
                return false;
            }
            Path aim = Path.of(target).toAbsolutePath().normalize();
            Path base = root.toAbsolutePath().normalize();
            if (!inside(base, aim)) {
                return false;
            }
            if (Files.exists(aim)) {
                return inside(base.toRealPath(), aim.toRealPath());
            }
            return true;
        } catch (IOException | RuntimeException ex) {
            return false;
        }
    }

    /** 目标必须在目录里面，不能正好用上级目录逃出去。 */
    private static boolean inside(Path base, Path aim) {
        Path relative = base.relativize(aim).normalize();
        if (relative.toString().isEmpty()) {
            return true;
        }
        for (Path part : relative) {
            if ("..".equals(part.toString())) {
                return false;
            }
        }
        return true;
    }
}
