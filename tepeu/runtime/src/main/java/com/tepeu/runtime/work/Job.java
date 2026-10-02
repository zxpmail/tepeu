package com.tepeu.runtime.work;

import com.tepeu.runtime.word.Reply;

/**
 * 做事里的一个类。检查口按名字来叫，把结果交回去。
 */
public interface Job {

    /** 这项能力的名字。 */
    String name();

    /** 做这一次，把结果交回。不写步骤记录。 */
    Reply run(Attempt attempt);
}
