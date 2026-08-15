/**
 * 后台任务通知 — 打开 /api/task-events SSE，收集自主任务完成/失败事件。
 * 模块级 store（跨组件共享）+ 单例 EventSource；可选浏览器 Notification API。
 * 任务事件低频，每 tab 直连即可，无需跨 tab leader 选举（见 ADR-013）。
 * 关联：TaskEventController、NotificationBell、ScheduleView。
 */
import { useSyncExternalStore } from 'react'

export interface TaskNotification {
  id: string
  kind: 'completed' | 'failed'
  scheduleId: string
  scheduleName: string
  workspaceId: string
  sessionId?: string
  message: string
  at: number
}

type StoreListener = () => void
type EventListener = (n: TaskNotification) => void

const MAX_HISTORY = 50

let es: EventSource | null = null
let notifications: TaskNotification[] = []
let unreadCount = 0
let version = 0
const storeListeners = new Set<StoreListener>()
const eventListeners = new Set<EventListener>()

function emitStore(): void {
  version += 1
  for (const fn of storeListeners) {
    try { fn() } catch { /* 单个订阅者异常不影响其他 */ }
  }
}

function emitEvent(n: TaskNotification): void {
  for (const fn of eventListeners) {
    try { fn(n) } catch { /* ignore */ }
  }
}

function openTaskEventSource(): void {
  if (es || typeof EventSource === 'undefined') return
  es = new EventSource('/api/task-events')
  es.onmessage = (e) => {
    const n = parseTaskEvent(e.data)
    if (!n) return
    notifications = [n, ...notifications].slice(0, MAX_HISTORY)
    unreadCount += 1
    notifyBrowser(n)
    emitStore()
    emitEvent(n)
  }
}

function parseTaskEvent(raw: string): TaskNotification | null {
  try {
    const d = JSON.parse(raw) as {
      type?: string
      scheduleId?: string
      scheduleName?: string
      workspaceId?: string
      sessionId?: string
      message?: string
    }
    if ((d?.type !== 'task_completed' && d?.type !== 'task_failed')
      || typeof d.scheduleId !== 'string'
      || typeof d.workspaceId !== 'string') {
      return null
    }
    return {
      id: `${d.type}-${d.scheduleId}-${Date.now().toString(36)}`,
      kind: d.type === 'task_completed' ? 'completed' : 'failed',
      scheduleId: d.scheduleId,
      scheduleName: d.scheduleName || d.scheduleId,
      workspaceId: d.workspaceId,
      sessionId: d.sessionId,
      message: d.message || '',
      at: Date.now(),
    }
  } catch { /* 非 JSON 帧忽略 */ }
  return null
}

/** 浏览器系统通知（可选）：用户已授权时弹出。 */
function notifyBrowser(n: TaskNotification): void {
  if (typeof Notification === 'undefined' || Notification.permission !== 'granted') return
  try {
    new Notification(n.kind === 'failed' ? '自主任务失败' : '自主任务完成', { body: n.message })
  } catch { /* 某些环境不支持构造 Notification 则忽略 */ }
}

/** 请求浏览器通知权限（用户手势内调用最佳）。 */
export function requestNotificationPermission(): void {
  if (typeof Notification === 'undefined' || Notification.permission !== 'default') return
  void Notification.requestPermission().catch(() => {})
}

// ---- 对外 API ----

/** React store 订阅（快照 = version，仅在新事件/状态变更时变化）。 */
export function subscribeTaskNotifications(fn: StoreListener): () => void {
  openTaskEventSource()
  storeListeners.add(fn)
  return () => { storeListeners.delete(fn) }
}

/** 新事件回调（不随 dismiss/已读触发），供 ScheduleView 按 workspaceId 精确刷新。 */
export function onTaskEvent(fn: EventListener): () => void {
  openTaskEventSource()
  eventListeners.add(fn)
  return () => { eventListeners.delete(fn) }
}

function getVersion(): number {
  return version
}

export function getTaskNotifications(): TaskNotification[] {
  return notifications
}

export function getUnreadCount(): number {
  return unreadCount
}

export function markAllTaskNotificationsRead(): void {
  if (unreadCount === 0) return
  unreadCount = 0
  emitStore()
}

export function clearTaskNotifications(): void {
  if (notifications.length === 0) return
  notifications = []
  unreadCount = 0
  emitStore()
}

/** React hook：通知列表 + 未读数 + 操作；首次挂载即打开 EventSource。 */
export function useTaskNotifications(): {
  notifications: TaskNotification[]
  unreadCount: number
  markAllRead: () => void
  clearAll: () => void
} {
  useSyncExternalStore(subscribeTaskNotifications, getVersion)
  return {
    notifications: getTaskNotifications(),
    unreadCount: getUnreadCount(),
    markAllRead: markAllTaskNotificationsRead,
    clearAll: clearTaskNotifications,
  }
}
