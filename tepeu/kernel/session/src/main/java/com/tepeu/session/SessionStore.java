package com.tepeu.session;

import com.tepeu.identity.Principal;
import com.tepeu.identity.SessionId;
import com.tepeu.identity.WorkspaceId;

/**
 * 打开一次对话。已有则读回，没有则建。第一刀一个默认对话。
 */
public interface SessionStore {

    SessionId DEFAULT = new SessionId("default");

    /** 已有则返回库里的主人与工作区；没有则按参数建。 */
    Session open(SessionId id, Principal owner, WorkspaceId workspace);
}
