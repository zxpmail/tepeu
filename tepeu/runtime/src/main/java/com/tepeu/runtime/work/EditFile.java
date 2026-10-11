package com.tepeu.runtime.work;

import com.tepeu.runtime.word.Ability;
import com.tepeu.runtime.word.Reply;
import com.tepeu.runtime.word.ResultKind;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 把文件里唯一的一段旧字换成新字。模型交来的名字是 Edit。
 * 范围和点头都由检查口先问过。这里只改这一处。
 */
public final class EditFile implements Job {

    /** 这项能力的名字。 */
    @Override
    public String name() {
        return Ability.EDIT;
    }

    /** 旧字必须正好出现一次。没有，或不止一处，都不改。 */
    @Override
    public Reply run(Attempt attempt) {
        String[] pieces = split(attempt.payload());
        if (pieces == null || pieces[0].isEmpty()) {
            return failed("旧的是空的");
        }
        try {
            Path path = Path.of(attempt.target());
            if (!Files.isRegularFile(path)) {
                return failed("找不到");
            }
            String text = Files.readString(path);
            int at = text.indexOf(pieces[0]);
            if (at < 0) {
                return failed("找不到");
            }
            if (text.indexOf(pieces[0], at + 1) >= 0) {
                return failed("不止一处");
            }
            String next = text.substring(0, at) + pieces[1] + text.substring(at + pieces[0].length());
            Files.writeString(path, next);
            return Reply.success("已改");
        } catch (IOException | RuntimeException ex) {
            return failed("改不到");
        }
    }

    /** 第一行是旧字长度，后面先是旧字，剩下是新字。对不上就是空。 */
    private static String[] split(String extra) {
        if (extra == null) {
            return null;
        }
        int newline = extra.indexOf('\n');
        if (newline < 0) {
            return null;
        }
        int length;
        try {
            length = Integer.parseInt(extra.substring(0, newline));
        } catch (NumberFormatException ex) {
            return null;
        }
        int start = newline + 1;
        if (length < 0 || start + length > extra.length()) {
            return null;
        }
        return new String[] {extra.substring(start, start + length), extra.substring(start + length)};
    }

    /** 改失败。正文说明原因。 */
    private static Reply failed(String body) {
        return new Reply(ResultKind.失败, null, body);
    }
}
