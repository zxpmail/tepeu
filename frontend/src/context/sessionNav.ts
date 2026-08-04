/**
 * 跨面板打开聊天会话 — ScheduleView / 通知铃等跳回 IdeShell 对话。
 * 支持「待打开」队列：次级面板尚无 IdeShell 订阅时先暂存，挂载后立刻消费。
 * 关联：App、IdeShell、NotificationBell、ScheduleView。
 */

export type OpenSessionRequest = {
  sessionId: string
  /** 若与当前工作区不同，由 App 先切换再打开会话 */
  workspaceId?: string
}

type OpenHandler = (req: OpenSessionRequest) => void

const listeners = new Set<OpenHandler>()
/** 无订阅者时暂存最近一次请求（次级面板 → 对话竞态） */
let pending: OpenSessionRequest | null = null

export const sessionNavBus = {
  /** 订阅「打开会话」；若有 pending 则立即投递一次 */
  subscribe(fn: OpenHandler): () => void {
    listeners.add(fn)
    if (pending) {
      const req = pending
      pending = null
      try {
        fn(req)
      } catch { /* ignore */ }
    }
    return () => { listeners.delete(fn) }
  },

  /**
   * 请求打开指定会话。
   * 无监听者时写入 pending，等 IdeShell 挂载后由 subscribe 冲刷。
   */
  openSession(sessionId: string, workspaceId?: string): void {
    if (!sessionId) return
    const req: OpenSessionRequest = { sessionId, workspaceId }
    if (listeners.size === 0) {
      pending = req
      return
    }
    for (const fn of listeners) {
      try {
        fn(req)
      } catch { /* ignore */ }
    }
  },

  /** 测试钩子：清空 pending 与订阅 */
  _resetForTests(): void {
    pending = null
    listeners.clear()
  },
}
