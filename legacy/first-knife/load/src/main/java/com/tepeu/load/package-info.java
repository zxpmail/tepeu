/**
 * 装配层。注入 persist / llm 实现，拼系统提示，登记斜杠命令与各具名操作。
 * 依赖只朝下：看见四层与 commands；不依赖 persist-sqlite / llm-fake 等实现模块，
 * 实现由 host 构造后经 {@link Assembly#wire} 交进来。
 */
package com.tepeu.load;
