/**
 * 后台任务通知铃铛 — 未读徽章 + 下拉事件列表。
 * 点击事件项：有 sessionId → 打开会话（可切工作区）；否则 → 跳转「自主」面板。
 * 首次打开时请求浏览器 Notification 权限（可选系统通知）。
 * 关联：useNotifications、/api/task-events（TaskEventController）、sessionNavBus。
 */
import { useEffect, useRef, useState } from 'react'
import {
  useTaskNotifications,
  requestNotificationPermission,
  type TaskNotification,
} from '../../hooks/useNotifications'
import { sessionNavBus } from '../../context/sessionNav'
import type { Panel } from '../../types'

interface NotificationBellProps {
  onNavigate: (panel: Panel) => void
  /** 优先使用：切工作区 + pending 安全打开会话；缺省回退 sessionNavBus */
  onOpenSession?: (sessionId: string, workspaceId?: string) => void | Promise<void>
}

function formatAt(at: number): string {
  try {
    return new Date(at).toLocaleTimeString()
  } catch {
    return ''
  }
}

export default function NotificationBell({ onNavigate, onOpenSession }: NotificationBellProps) {
  const { notifications, unreadCount, markAllRead, clearAll } = useTaskNotifications()
  const [open, setOpen] = useState(false)
  const boxRef = useRef<HTMLDivElement | null>(null)

  const unreadHasFailed = notifications.slice(0, unreadCount).some(n => n.kind === 'failed')

  // 点击外部关闭下拉
  useEffect(() => {
    if (!open) return
    const onDocClick = (e: MouseEvent) => {
      if (boxRef.current && !boxRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', onDocClick)
    return () => document.removeEventListener('mousedown', onDocClick)
  }, [open])

  const toggle = () => {
    const next = !open
    setOpen(next)
    if (next) {
      requestNotificationPermission()
      markAllRead()
    }
  }

  const handleItem = (n: TaskNotification) => {
    setOpen(false)
    if (n.sessionId) {
      if (onOpenSession) {
        void onOpenSession(n.sessionId, n.workspaceId)
      } else {
        sessionNavBus.openSession(n.sessionId, n.workspaceId)
        onNavigate('chat')
      }
    } else {
      onNavigate('schedule')
    }
  }

  return (
    <div className="relative" ref={boxRef}>
      <button
        type="button"
        data-testid="notification-bell"
        className="relative flex items-center justify-center w-7 h-7 rounded"
        style={{ color: 'var(--color-text-secondary)' }}
        onClick={toggle}
        title="后台任务通知"
      >
        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
          <path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
          <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
        </svg>
        {unreadCount > 0 && (
          <span
            data-testid="notification-badge"
            className="absolute -top-0.5 -right-0.5 min-w-[15px] h-[15px] px-0.5 rounded-full text-[10px] leading-[15px] text-center"
            style={{
              backgroundColor: unreadHasFailed
                ? 'var(--color-danger, #c44)'
                : 'var(--color-accent)',
              color: '#fff',
            }}
          >
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div
          data-testid="notification-dropdown"
          className="absolute right-0 mt-1 w-80 max-h-80 overflow-y-auto rounded border shadow-lg z-50"
          style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg-secondary)' }}
        >
          <div className="flex items-center justify-between px-3 py-2 border-b text-xs" style={{ borderColor: 'var(--color-border)', color: 'var(--color-text-secondary)' }}>
            <span>后台任务</span>
            {notifications.length > 0 && (
              <button type="button" className="underline" style={{ color: 'var(--color-accent)' }} onClick={clearAll}>
                全部清除
              </button>
            )}
          </div>
          {notifications.length === 0 ? (
            <div className="px-3 py-4 text-xs" style={{ color: 'var(--color-text-dim)' }}>暂无任务通知。</div>
          ) : (
            <ul>
              {notifications.map(n => (
                <li key={n.id} className="border-b last:border-b-0" style={{ borderColor: 'var(--color-border)' }}>
                  <button
                    type="button"
                    data-testid={`notification-item-${n.kind}`}
                    className="w-full text-left px-3 py-2 hover:bg-black/5"
                    onClick={() => handleItem(n)}
                  >
                    <div className="flex items-center gap-2">
                      <span
                        className="text-[11px] shrink-0"
                        style={{ color: n.kind === 'failed' ? 'var(--color-danger, #c44)' : 'var(--color-accent)' }}
                      >
                        {n.kind === 'failed' ? '失败' : '完成'}
                      </span>
                      <span className="text-xs font-medium truncate" style={{ color: 'var(--color-text)' }}>
                        {n.scheduleName}
                      </span>
                      <span className="ml-auto text-[10px] shrink-0" style={{ color: 'var(--color-text-dim)' }}>
                        {formatAt(n.at)}
                      </span>
                    </div>
                    {n.message && (
                      <div className="text-[11px] mt-0.5 line-clamp-2" style={{ color: 'var(--color-text-secondary)' }}>
                        {n.message}
                      </div>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
