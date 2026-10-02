package com.tepeu.runtime.work;

import com.tepeu.runtime.word.Reply;

import java.util.List;

/**
 * 检查口放行之后交给做事的这一次。
 * 几行是给模型看已经准备好的，做事不改字。
 */
public record Attempt(String target, String payload, List<String> shown) {

    /** 没写的目标、正文和几行，都当成空。 */
    public Attempt {
        target = target == null ? "" : target;
        payload = payload == null ? "" : payload;
        shown = shown == null ? List.of() : List.copyOf(shown);
    }
}
