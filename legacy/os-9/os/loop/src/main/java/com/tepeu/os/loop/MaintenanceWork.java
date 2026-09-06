package com.tepeu.os.loop;

import com.tepeu.os.identity.TurnContext;
import com.tepeu.os.session.Session;

/**
 * maintenance 窗口内的协作步进。不得 claim Inbox、不得再 {@code SessionLoop.run}。
 * 返回 true 表示还有工作。
 */
@FunctionalInterface
public interface MaintenanceWork {
    boolean step(Session session, TurnContext ctx);
}
