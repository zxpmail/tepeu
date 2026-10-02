package com.tepeu.runtime.view;

import java.util.List;

/**
 * 记忆。没交进来，给模型看的内容里就没有它。
 * 找回来的字只给模型看，不能当证据。
 */
public interface Memory {

    /** 找以前的内容。 */
    List<String> recall();
}
