/**
 * 跨面板打开聊天会话 — ScheduleView 等次级页跳回 IdeShell 对话。
 * 关联：App、IdeShell、ScheduleView。
 */

type OpenHandler = (sessionId: string) => void

const listeners = new Set<OpenHandler>()

export const sessionNavBus = {
  /** 订阅「打开会话」请求 */
  subscribe(fn: OpenHandler): () => void {
    listeners.add(fn)
    return () => { listeners.delete(fn) }
  },
  /** 请求打开指定会话（IdeShell 收到后 loadSession） */
  openSession(sessionId: string): void {
    if (!sessionId) return
    for (const fn of listeners) {
      try { fn(sessionId) } catch { /* ignore */ }
    }
  },
}
