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

    /** 读目标文件。字会交给检查口，由它决定进不进步骤记录。 */
    @Override
    public Reply run(Attempt attempt) {
        try {
            String text = Files.readString(Path.of(attempt.target()));
            return Reply.success(text);
        } catch (IOException | RuntimeException ex) {
            return new Reply(ResultKind.失败, null, "");
        }
    }
}
