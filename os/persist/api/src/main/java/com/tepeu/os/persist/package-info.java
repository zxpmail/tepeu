/**
 * persist 组件 — 访问口是 {@link Persist}；引擎口是 {@link PersistEngine}。
 * 领域组件只访问，不建连接、不关库。实现在 persist/sqlite。
 * <p>
 * 本组件<b>不</b>引入 slf4j。不知道 kernel / approvals（那是 host 的两套库）。
 * 引擎 prepare 失败走 JDK {@code System.Logger}，不打 SQL / JDBC URL。库身份与关库诊断在 host。
 */
package com.tepeu.os.persist;
