package com.tepeu.runtime.answer;

/**
 * 回答这一次问模型还够不够。次数只扣在自己这里，不进步骤记录。
 * 问一次算 1。到 0 就不够，不会扣成负数，也不能装成永远够。
 */
public final class Fee {

    private int left;

    /** 交进来时给出还能问几次。负数没有意义，不能当成永远够。 */
    public Fee(int times) {
        if (times < 0) {
            throw new IllegalArgumentException("费用不能装成永远够");
        }
        this.left = times;
    }

    /** 够了先扣 1。不够就原样留下，不扣成负数。 */
    public boolean enough() {
        if (left <= 0) {
            return false;
        }
        left = left - 1;
        return true;
    }

    /** 还剩几次。测试用来确认没扣成负数。 */
    public int left() {
        return left;
    }
}
