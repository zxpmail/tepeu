package com.tepeu.agent.hook;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 幻觉门禁：父目录存在性 + 声称写入路径扫描。 */
class HallucinationGuardTest {

    private final HallucinationGuard guard = new HallucinationGuard();

    @TempDir
    Path root;

    @Test
    void checkWriteParent_okWhenParentExists() throws Exception {
        Path file = root.resolve("a.txt");
        assertNull(guard.checkWriteParent(file));
        Files.createDirectories(root.resolve("sub"));
        assertNull(guard.checkWriteParent(root.resolve("sub/b.txt")));
    }

    @Test
    void checkWriteParent_failsWhenParentMissing() {
        String err = guard.checkWriteParent(root.resolve("missing/a.txt"));
        assertTrue(err != null && err.contains("HALLUCINATION"));
    }

    @Test
    void findMissingClaimedPaths_detectsAbsentFiles() throws Exception {
        Files.writeString(root.resolve("real.txt"), "ok");
        String text = "我已经创建 report.md，并写入了 real.txt";
        List<String> missing = guard.findMissingClaimedPaths(text, root);
        assertTrue(missing.contains("report.md"));
        assertFalse(missing.contains("real.txt"));
    }
}
