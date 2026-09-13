package com.tepeu.session;

import com.tepeu.identity.Principal;
import com.tepeu.identity.SessionId;
import com.tepeu.identity.WorkspaceId;

/**
 * 打开一次对话。已有则读回，没有则建。第一刀一个默认对话。
 */
public interface SessionStore {

    SessionId DEFAULT = new SessionId("default");

    /** 没有则按参数建；已有则要求主人与工作区一致（不一致失败），并返回库里那条。 */
    Session open(SessionId id, Principal owner, WorkspaceId workspace);
}
