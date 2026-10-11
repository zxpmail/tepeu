package com.tepeu.runtime.work;

import com.tepeu.runtime.word.Ability;
import com.tepeu.runtime.word.Reply;
import com.tepeu.runtime.word.ResultKind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * 按名字在限制里找文件。模型交来的名字是 Glob。
 * 范围由检查口先问过。这里不经过命令外壳。
 */
public final class GlobFiles implements Job {

    /** 一次最多交回这么多个路径。 */
    static final int LIMIT = 200;

    /** 这项能力的名字。 */
    @Override
    public String name() {
        return Ability.GLOB;
    }

    /** 从目录往下对名字。没有斜线时，名字在哪一层都算。 */
    @Override
    public Reply run(Attempt attempt) {
        String pattern = attempt.payload() == null ? "" : attempt.payload().replace('\\', '/');
        if (pattern.isBlank()) {
            return new Reply(ResultKind.失败, null, "式子是空的");
        }
        if (!pattern.contains("/")) {
            pattern = "**/" + pattern;
        }
        try {
            Path root = Path.of(attempt.target());
            if (!Files.isDirectory(root)) {
                return new Reply(ResultKind.失败, null, "不是目录");
            }
            return Reply.success(match(root, pattern));
        } catch (IOException | RuntimeException ex) {
            return new Reply(ResultKind.失败, null, "找不到");
        }
    }

    /** 收集对得上的文件路径。不跟着链接走。 */
    private static String match(Path root, String pattern) throws IOException {
        StringBuilder found = new StringBuilder();
        int count = 0;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.filter(path -> !Files.isSymbolicLink(path) && Files.isRegularFile(path)).toList()) {
                String relative = root.relativize(path).toString().replace('\\', '/');
                if (!glob(relative, pattern)) {
                    continue;
                }
                if (count == LIMIT) {
                    found.append('\n').append("还有更多");
                    break;
                }
                if (count > 0) {
                    found.append('\n');
                }
                found.append(path);
                count = count + 1;
            }
        }
        return found.toString();
    }

    /** 路径对式子。** 可以跨目录。 */
    static boolean glob(String path, String pattern) {
        if (pattern.startsWith("**/")) {
            String rest = pattern.substring(3);
            if (glob(path, rest)) {
                return true;
            }
            int slash = path.indexOf('/');
            while (slash >= 0) {
                if (glob(path.substring(slash + 1), rest)) {
                    return true;
                }
                slash = path.indexOf('/', slash + 1);
            }
            return false;
        }
        int patternSlash = pattern.indexOf('/');
        int pathSlash = path.indexOf('/');
        String patternPart = patternSlash < 0 ? pattern : pattern.substring(0, patternSlash);
        String pathPart = pathSlash < 0 ? path : path.substring(0, pathSlash);
        if (!segment(pathPart, patternPart)) {
            return false;
        }
        if (patternSlash < 0) {
            return pathSlash < 0;
        }
        if (pathSlash < 0) {
            return false;
        }
        return glob(path.substring(pathSlash + 1), pattern.substring(patternSlash + 1));
    }

    /** 一段名字对一段式子。星号可以是任意字。 */
    private static boolean segment(String text, String pattern) {
        int textAt = 0;
        int patternAt = 0;
        int star = -1;
        int mark = -1;
        while (textAt < text.length()) {
            if (patternAt < pattern.length()
                    && (pattern.charAt(patternAt) == '?' || pattern.charAt(patternAt) == text.charAt(textAt))) {
                textAt = textAt + 1;
                patternAt = patternAt + 1;
            } else if (patternAt < pattern.length() && pattern.charAt(patternAt) == '*') {
                star = patternAt;
                patternAt = patternAt + 1;
                mark = textAt;
            } else if (star >= 0) {
                patternAt = star + 1;
                mark = mark + 1;
                textAt = mark;
            } else {
                return false;
            }
        }
        while (patternAt < pattern.length() && pattern.charAt(patternAt) == '*') {
            patternAt = patternAt + 1;
        }
        return patternAt == pattern.length();
    }
}
