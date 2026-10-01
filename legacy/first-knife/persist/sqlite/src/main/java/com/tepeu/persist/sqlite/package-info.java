/**
 * persist 的 SQLite 实现。MyBatis-Plus 只在本模块。
 * 不知道 session / policy。开库失败走 JDK {@code System.Logger}，不打 SQL / JDBC URL / 正文。
 */
package com.tepeu.persist.sqlite;
