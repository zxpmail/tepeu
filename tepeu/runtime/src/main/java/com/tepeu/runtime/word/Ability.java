package com.tepeu.runtime.word;

/**
 * 做事这项能力的名字。检查口按这个名字去找。
 */
public final class Ability {

    /** 问模型 */
    public static final String ASK = "问模型";
    /** 读东西。模型交来时叫 Read。 */
    public static final String READ = "读东西";
    /** 写东西。模型交来时叫 Write。 */
    public static final String WRITE = "写东西";
    /** 在文件里查找。模型交来时叫 Grep。 */
    public static final String GREP = "Grep";
    /** 按名字找文件。模型交来时叫 Glob。 */
    public static final String GLOB = "Glob";
    /** 改文件里唯一的一段。模型交来时叫 Edit。 */
    public static final String EDIT = "Edit";
    /** 跑命令 */
    public static final String RUN = "跑命令";

    private Ability() {
    }
}
