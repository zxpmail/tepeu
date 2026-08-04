/**
 * 跨 tab 共享文件事件 SSE 连接。
 * 所有 tab 用 BroadcastChannel 同步；仅一个「leader」tab 持有 EventSource('/api/events')，
 * 收到 file_changed 后经 channel 广播给所有 tab（含自己）。leader 用 localStorage 心跳续约，
 * 失效后其他 tab 接管。旧浏览器无 BroadcastChannel 时回退为每 tab 直连。
 * 关联：WorkspaceEventsProvider、/api/events（FileEventsController）。
 */

const CHANNEL_NAME = 'tepeu-file-events'
const LEADER_KEY = 'tepeu-sse-leader'
const HEARTBEAT_MS = 2000
const LEADER_TTL_MS = 6000
const CLAIM_TOKEN = Math.random().toString(36).slice(2) + Date.now().toString(36)

export interface FileChangeEvent {
  path: string
  workspaceId?: string
}

export function openSharedFileEvents(
  onEvent: (path: string, workspaceId?: string) => void,
): () => void {
  if (typeof BroadcastChannel === 'undefined') {
    // 回退：无 BroadcastChannel 的旧环境，每 tab 直连
    const es = new EventSource('/api/events')
    es.onmessage = (e) => {
      const ev = parseEvent(e.data)
      if (ev) onEvent(ev.path, ev.workspaceId)
    }
    return () => es.close()
  }

  const channel = new BroadcastChannel(CHANNEL_NAME)
  let leader = false
  let es: EventSource | null = null
  let heartbeatTimer: ReturnType<typeof setInterval> | null = null
  let claimTimer: ReturnType<typeof setTimeout> | null = null
  let disposed = false

  const readLeader = (): { id: string; ts: number } | null => {
    try {
      const raw = localStorage.getItem(LEADER_KEY)
      if (!raw) return null
      const parsed = JSON.parse(raw) as { id: string; ts: number }
      return typeof parsed?.id === 'string' && typeof parsed?.ts === 'number' ? parsed : null
    } catch {
      return null
    }
  }

  const heartbeat = () => {
    try {
      localStorage.setItem(LEADER_KEY, JSON.stringify({ id: CLAIM_TOKEN, ts: Date.now() }))
    } catch { /* localStorage 不可用则忽略 */ }
  }

  const openEvents = () => {
    es = new EventSource('/api/events')
    es.onmessage = (e) => {
      const ev = parseEvent(e.data)
      if (!ev) return
      // BroadcastChannel 不会把消息回传给发送者 → leader tab 必须本地消费，否则自己收不到
      onEvent(ev.path, ev.workspaceId)
      channel.postMessage(ev)
    }
    // EventSource 断线自动重连，无需额外处理
  }

  const closeEvents = () => {
    es?.close()
    es = null
  }

  /** 尝试接管 leader：写入自己的 claim 后读回确认没有被并发覆盖。 */
  const becomeLeader = (): boolean => {
    const now = Date.now()
    const current = readLeader()
    if (current && now - current.ts < LEADER_TTL_MS) return false
    heartbeat()
    return readLeader()?.id === CLAIM_TOKEN
  }

  const tryLead = () => {
    if (disposed) return
    if (leader) {
      heartbeat()
      return
    }
    if (becomeLeader()) {
      leader = true
      openEvents()
      heartbeat()
      heartbeatTimer = setInterval(heartbeat, HEARTBEAT_MS)
    } else {
      // 现有 leader 存活或接管失败：稍后重试（处理 leader 崩溃/过期）
      claimTimer = setTimeout(tryLead, LEADER_TTL_MS)
    }
  }

  channel.onmessage = (e) => {
    const ev = e.data as FileChangeEvent
    if (ev && typeof ev.path === 'string') onEvent(ev.path, ev.workspaceId)
  }

  const onUnload = () => {
    if (leader) {
      try { localStorage.removeItem(LEADER_KEY) } catch { /* ignore */ }
    }
  }
  window.addEventListener('beforeunload', onUnload)

  tryLead()

  return () => {
    disposed = true
    window.removeEventListener('beforeunload', onUnload)
    if (heartbeatTimer) clearInterval(heartbeatTimer)
    if (claimTimer) clearTimeout(claimTimer)
    if (leader) {
      try { localStorage.removeItem(LEADER_KEY) } catch { /* ignore */ }
      closeEvents()
    }
    channel.close()
  }
}

function parseEvent(raw: string): FileChangeEvent | null {
  try {
    const d = JSON.parse(raw) as { type?: string; path?: string; workspaceId?: string }
    if (d?.type === 'file_changed' && typeof d.path === 'string') {
      return { path: d.path, workspaceId: d.workspaceId }
    }
  } catch { /* 非 JSON 帧忽略 */ }
  return null
}
