package com.tepeu.runtime.round;

import com.tepeu.runtime.word.Ability;

import java.util.ArrayList;
import java.util.List;

/**
 * 从问模型的成功正文里认出整行点名。
 * 一轮用它决定放行读、写、跑。检查口不认这些行。
 */
final class PointNames {

    /** 一项点名。写的正文、跑的参数都在 payload 里。 */
    record Item(String name, String target, String payload) {
    }

    private PointNames() {
    }

    /** 按正文从上到下认出点名。夹在句子里的名字不算。 */
    static List<Item> parse(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        String[] lines = body.split("\\R", -1);
        List<Item> items = new ArrayList<>();
        int index = 0;
        while (index < lines.length) {
            Hit hit = hit(lines[index]);
            if (hit == null) {
                index = index + 1;
                continue;
            }
            int next = index + 1;
            while (next < lines.length && hit(lines[next]) == null) {
                next = next + 1;
            }
            List<String> following = List.of(lines).subList(index + 1, next);
            if (Ability.WRITE.equals(hit.name())) {
                String text = String.join("\n", following);
                if (text.isBlank()) {
                    index = next;
                    continue;
                }
                items.add(new Item(hit.name(), hit.target(), text));
                index = next;
                continue;
            }
            if (Ability.RUN.equals(hit.name())) {
                items.add(new Item(hit.name(), hit.target(), args(following)));
                index = next;
                continue;
            }
            items.add(new Item(hit.name(), hit.target(), ""));
            index = index + 1;
        }
        return List.copyOf(items);
    }

    /** 跑命令下面的非空行，每行一个参数。 */
    private static String args(List<String> following) {
        StringBuilder args = new StringBuilder();
        for (String line : following) {
            if (line.isBlank()) {
                continue;
            }
            if (args.length() > 0) {
                args.append('\n');
            }
            args.append(line);
        }
        return args.toString();
    }

    /** 去掉两端空白后，整行是三个名字之一，或名字加恰好一个空格再加目标。 */
    private static Hit hit(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.strip();
        for (String name : List.of(Ability.READ, Ability.WRITE, Ability.RUN)) {
            if (trimmed.equals(name)) {
                return new Hit(name, "");
            }
            String prefix = name + " ";
            if (!trimmed.startsWith(prefix)) {
                continue;
            }
            String rest = trimmed.substring(prefix.length());
            if (rest.startsWith(" ") || rest.startsWith("\t")) {
                return null;
            }
            return new Hit(name, rest.strip());
        }
        return null;
    }

    /** 这一行点了哪个名字、目标是什么。 */
    private record Hit(String name, String target) {
    }
}
