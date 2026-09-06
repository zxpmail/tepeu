package com.tepeu.os.compose;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 角色档机器门。根包 interface/enum 不得 import {@code *.local}；
 * {@code InMemory*} 不得进 {@code src/main}。提示词记不住，测试记得住。
 */
class PackageRoleTest {

    private static final Pattern PACKAGE = Pattern.compile("^package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern IMPORT_LOCAL = Pattern.compile("^import\\s+com\\.tepeu\\.os\\.\\w+\\.local\\.", Pattern.MULTILINE);
    private static final Pattern FIRST_TYPE = Pattern.compile(
            "(?m)^(?:public\\s+|final\\s+|sealed\\s+|abstract\\s+)*(interface|enum|class|record)\\s+\\w+");

    @Test
    void rootContractsDoNotImportLocal() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path java : mainJavaFiles()) {
            String text = Files.readString(java, StandardCharsets.UTF_8);
            Matcher pkg = PACKAGE.matcher(text);
            if (!pkg.find() || !isComponentRoot(pkg.group(1))) {
                continue;
            }
            Matcher type = FIRST_TYPE.matcher(text);
            if (type.find() && ("interface".equals(type.group(1)) || "enum".equals(type.group(1)))
                    && IMPORT_LOCAL.matcher(text).find()) {
                hits.add(rel(java) + " — 根包契约 import *.local");
            }
        }
        assertTrue(hits.isEmpty(), String.join("\n", hits));
    }

    @Test
    void inMemoryTypesStayInTest() throws IOException {
        List<String> hits = new ArrayList<>();
        for (Path java : mainJavaFiles()) {
            if (java.getFileName().toString().startsWith("InMemory")) {
                hits.add(rel(java));
            }
        }
        assertTrue(hits.isEmpty(), "InMemory* 进了 src/main:\n" + String.join("\n", hits));
    }

    private static List<Path> mainJavaFiles() throws IOException {
        Path os = osRoot();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(os)) {
            walk.filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return !s.contains("/target/")
                                && s.contains("/src/main/java/")
                                && s.endsWith(".java");
                    })
                    .forEach(files::add);
        }
        if (files.isEmpty()) {
            fail("no main java under " + os);
        }
        return files;
    }

    private static Path osRoot() {
        Path here = Path.of("").toAbsolutePath();
        if (Files.isDirectory(here.resolve("session/src/main/java"))) {
            return here;
        }
        if (Files.isDirectory(here.getParent().resolve("session/src/main/java"))) {
            return here.getParent();
        }
        Path fromRepo = here.resolve("os");
        if (Files.isDirectory(fromRepo.resolve("session/src/main/java"))) {
            return fromRepo;
        }
        fail("cannot locate os/ from " + here);
        return here;
    }

    /** {@code com.tepeu.os.loop} 是根包；{@code com.tepeu.os.loop.local} 不是。 */
    private static boolean isComponentRoot(String pkg) {
        String[] parts = pkg.split("\\.");
        return parts.length == 4 && "com".equals(parts[0]) && "tepeu".equals(parts[1]) && "os".equals(parts[2]);
    }

    private static String rel(Path java) {
        return java.toString().replace('\\', '/');
    }
}
