package com.tepeu.runtime.work;

import com.tepeu.runtime.word.Ability;
import com.tepeu.runtime.word.Reply;
import com.tepeu.runtime.word.ResultKind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 读东西。把文件里的字交回来。不问点头。
 * 读不到就交回失败。不写步骤记录。
 */
public final class ReadFile implements Job {

    /** 这项能力的名字。 */
    @Override
    public String name() {
        return Ability.READ;
    }

    /** 读目标。目录只交回里面的名字。字会交给检查口，由它决定进不进步骤记录。 */
    @Override
    public Reply run(Attempt attempt) {
        try {
            Path path = Path.of(attempt.target());
            if (Files.isDirectory(path)) {
                return Reply.success(namesIn(path));
            }
            return Reply.success(Files.readString(path));
        } catch (IOException | RuntimeException ex) {
            return new Reply(ResultKind.失败, null, "读不到");
        }
    }

    /** 目录里每一项的名字，一行一个。 */
    private static String namesIn(Path path) throws IOException {
        StringBuilder names = new StringBuilder();
        try (var children = Files.list(path)) {
            for (Path child : children.sorted().toList()) {
                if (names.length() > 0) {
                    names.append('\n');
                }
                names.append(child.getFileName());
            }
        }
        return names.toString();
    }
}
