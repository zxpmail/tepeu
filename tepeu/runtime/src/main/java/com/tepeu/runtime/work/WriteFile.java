package com.tepeu.runtime.work;

import com.tepeu.runtime.word.Ability;
import com.tepeu.runtime.word.Reply;
import com.tepeu.runtime.word.ResultKind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 写东西。把「要做什么」写进目标文件。
 * 范围和点头都由检查口先问过。这里只写。
 */
public final class WriteFile implements Job {

    /** 这项能力的名字。 */
    @Override
    public String name() {
        return Ability.WRITE;
    }

    /** 把这一次的正文写进文件。成功就按成功交回。 */
    @Override
    public Reply run(Attempt attempt) {
        try {
            Path path = Path.of(attempt.target());
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, attempt.payload());
            return Reply.success(attempt.payload());
        } catch (IOException | RuntimeException ex) {
            return new Reply(ResultKind.失败, null, "");
        }
    }
}
