package com.tepeu.runtime.work;

import com.tepeu.runtime.word.Ability;
import com.tepeu.runtime.word.Reply;
import com.tepeu.runtime.word.ResultKind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;

/**
 * 在限制里的文件中查找。模型交来的名字是 Grep。
 * 范围由检查口先问过。这里不经过命令外壳。
 */
public final class GrepFiles implements Job {

    /** 一次最多交回这么多行，多了就说明还有。 */
    static final int LIMIT = 100;

    /** 超过这个大小的文件不读。 */
    private static final long MAX_BYTES = 1_000_000L;

    /** 这项能力的名字。 */
    @Override
    public String name() {
        return Ability.GREP;
    }

    /** 用正则找。目录就往下找，文件就只看这一个。 */
    @Override
    public Reply run(Attempt attempt) {
        Pattern pattern;
        try {
            if (attempt.payload() == null || attempt.payload().isBlank()) {
                return failed("式子是空的");
            }
            pattern = Pattern.compile(attempt.payload());
        } catch (PatternSyntaxException ex) {
            return failed("式子不对");
        }
        try {
            Path root = Path.of(attempt.target());
            if (Files.isDirectory(root)) {
                return Reply.success(searchTree(root, pattern));
            }
            return Reply.success(searchFile(root, pattern, new StringBuilder(), new int[] {0}));
        } catch (IOException | RuntimeException ex) {
            return failed("找不到");
        }
    }

    /** 从目录往下找普通文件。不跟着链接走。 */
    private static String searchTree(Path root, Pattern pattern) throws IOException {
        StringBuilder found = new StringBuilder();
        int[] count = {0};
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.filter(path -> !Files.isSymbolicLink(path)).toList()) {
                if (count[0] > LIMIT) {
                    break;
                }
                if (Files.isRegularFile(path)) {
                    searchFile(path, pattern, found, count);
                }
            }
        }
        return found.toString();
    }

    /** 在一个文件里找。太大或读不出就跳过。 */
    private static String searchFile(Path path, Pattern pattern, StringBuilder found, int[] count) throws IOException {
        if (count[0] > LIMIT || Files.size(path) > MAX_BYTES) {
            return found.toString();
        }
        String text;
        try {
            text = Files.readString(path);
        } catch (IOException ex) {
            return found.toString();
        }
        String[] lines = text.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (!pattern.matcher(lines[index]).find()) {
                continue;
            }
            if (count[0] == LIMIT) {
                if (count[0] > 0) {
                    found.append('\n');
                }
                found.append("还有更多");
                count[0] = count[0] + 1;
                return found.toString();
            }
            if (count[0] > 0) {
                found.append('\n');
            }
            found.append(path).append(':').append(index + 1).append(':').append(lines[index]);
            count[0] = count[0] + 1;
        }
        return found.toString();
    }

    /** 查找失败。正文说明原因。 */
    private static Reply failed(String body) {
        return new Reply(ResultKind.失败, null, body);
    }
}
