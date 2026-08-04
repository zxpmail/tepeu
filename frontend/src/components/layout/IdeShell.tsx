/**
 * IDE 三栏主壳 — 左会话/文件、中对话、右预览。
 * 关联：SessionSidebar、ChatView、RightFilePanel、App；顶栏展示会话用量与工作区累计。
 */
import { useState, useCallback, useEffect, useRef } from 'react'
import SessionSidebar from './SessionSidebar'
import RightFilePanel from '../files/RightFilePanel'
import ChatView from '../views/ChatView'
import ThemeToggle from '../common/ThemeToggle'
import NotificationBell from './NotificationBell'
import { api } from '../../api/client'
import { sessionNavBus } from '../../context/sessionNav'
import type { BudgetStatus, Panel, Theme, Workspace, SessionStats, WorkspaceStats } from '../../types'
import type { LastUsage } from '../../hooks/useChat'

interface IdeShellProps {
  workspace: Workspace | null
  theme: Theme
  onToggleTheme: (t: Theme) => void
  onNavigate: (panel: Panel) => void
}

export default function IdeShell({
  workspace,
  theme,
  onToggleTheme,
  onNavigate,
}: IdeShellProps) {
  const [leftOpen, setLeftOpen] = useState(true)
  const [rightOpen, setRightOpen] = useState(false)
  const [openFile, setOpenFile] = useState<string | null>(null)
  const [sessionId, setSessionId] = useState<string | undefined>()
  const [chatStats, setChatStats] = useState<{
    lastUsage: LastUsage | null
    sessionStats: SessionStats | null
    queueLength: number
  }>({ lastUsage: null, sessionStats: null, queueLength: 0 })
  const [chatActions, setChatActions] = useState<{
    reset: () => void
    loadSession: (id: string) => Promise<void>
  } | null>(null)
  const [wsStats, setWsStats] = useState<WorkspaceStats | null>(null)
  const [budgetStatus, setBudgetStatus] = useState<BudgetStatus | null>(null)
  const pendingSessionRef = useRef<string | null>(null)

  const handleOpenFile = useCallback((path: string) => {
    setOpenFile(path)
    setRightOpen(true)
  }, [])

  // 自主面板等请求打开历史会话
  useEffect(() => {
    return sessionNavBus.subscribe((id) => {
      setSessionId(id)
      if (chatActions) {
        void chatActions.loadSession(id)
      } else {
        pendingSessionRef.current = id
      }
    })
  }, [chatActions])

  useEffect(() => {
    if (!chatActions || !pendingSessionRef.current) return
    const id = pendingSessionRef.current
    pendingSessionRef.current = null
    setSessionId(id)
    void chatActions.loadSession(id)
  }, [chatActions])

  // 工作区切换或会话用量变化后刷新累计与预算状态（§3.5 / M2.4）
  useEffect(() => {
    if (!workspace?.id) {
      setWsStats(null)
      setBudgetStatus(null)
      return
    }
    let cancelled = false
    api.getWorkspaceStats(workspace.id)
      .then(s => { if (!cancelled) setWsStats(s) })
      .catch(() => { if (!cancelled) setWsStats(null) })
    api.getWorkspaceCost(workspace.id)
      .then(s => { if (!cancelled) setBudgetStatus(s) })
      .catch(() => { if (!cancelled) setBudgetStatus(null) })
    return () => { cancelled = true }
  }, [workspace?.id, chatStats.sessionStats])

  const prompt = chatStats.lastUsage?.promptTokens ?? 0
  const comp = chatStats.lastUsage?.completionTokens ?? 0
  const sessionCost = chatStats.sessionStats?.totalCostUsd ?? chatStats.lastUsage?.costUsd ?? 0
  const wsTokens = wsStats?.totalTokens ?? 0
  const wsCost = wsStats?.totalCostUsd ?? 0

  return (
    <div data-testid="ide-shell" className="ide-shell h-full flex flex-col overflow-hidden">
      {/* 顶栏 */}
      <header
        className="ide-topbar shrink-0 h-10 flex items-center gap-2 px-3 border-b select-none"
        style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg-secondary)' }}
      >
        <button
          type="button"
          className="text-xs px-1.5 py-1 rounded"
          style={{ color: 'var(--color-text-secondary)' }}
          onClick={() => setLeftOpen(o => !o)}
          title={leftOpen ? '收起侧栏' : '展开侧栏'}
        >
          ☰
        </button>
        <span className="font-semibold text-sm" style={{ color: 'var(--color-text)' }}>Tepeu</span>
        <span className="text-xs truncate max-w-[40%]" style={{ color: 'var(--color-text-dim)' }}>
          {workspace ? workspace.name : '未选择工作区'}
        </span>

        <div className="ml-auto flex items-center gap-2 text-[11px]" style={{ color: 'var(--color-text-dim)' }}>
          <span title="本轮输入/输出 Token">↑{prompt} ↓{comp}</span>
          <span title="当前会话费用">会话 ${sessionCost.toFixed(4)}</span>
          <span title="当前工作区累计 Token/费用">区 {wsTokens} · ${wsCost.toFixed(4)}</span>
          {budgetStatus?.blocked && (
            <button
              type="button"
              data-testid="budget-blocked-badge"
              className="text-[11px] px-1.5 py-0.5 rounded border"
              style={{
                borderColor: 'color-mix(in srgb, var(--color-danger) 40%, var(--color-border))',
                color: 'var(--color-danger)',
              }}
              onClick={() => onNavigate('cost')}
              title="预算硬门禁已生效，点击打开成本仪表盘"
            >
              预算已耗尽
            </button>
          )}
          {budgetStatus?.alert && !budgetStatus.blocked && (
            <button
              type="button"
              data-testid="budget-alert-badge"
              className="text-[11px] px-1.5 py-0.5 rounded border"
              style={{
                borderColor: 'color-mix(in srgb, var(--color-danger) 35%, var(--color-border))',
                color: 'var(--color-danger)',
              }}
              onClick={() => onNavigate('cost')}
              title="已超过预算告警阈值，点击打开成本仪表盘"
            >
              预算告警 {Math.min(100, Math.round((budgetStatus.usageRatio || 0) * 100))}%
            </button>
          )}
          {chatStats.queueLength > 0 && <span>排队{chatStats.queueLength}</span>}
          <button
            type="button"
            className="text-xs px-1.5 py-1 rounded"
            style={{ color: 'var(--color-text-secondary)' }}
            onClick={() => setRightOpen(o => !o)}
            title={rightOpen ? '收起预览' : '展开预览'}
          >
            ▥
          </button>
          <NotificationBell onNavigate={onNavigate} />
          <ThemeToggle theme={theme} onToggle={onToggleTheme} />
        </div>
      </header>

      <div className="flex flex-1 min-h-0 overflow-hidden">
        {/* 左栏 */}
        <aside
          className={`ide-left border-r shrink-0 overflow-hidden ${leftOpen ? 'ide-panel-open' : 'ide-panel-closed'}`}
          style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-sidebar-bg)' }}
        >
          <div className="ide-left-inner h-full">
            <SessionSidebar
              workspaceId={workspace?.id}
              workspaceName={workspace?.name}
              sessionId={sessionId}
              onSelectSession={(id) => {
                if (id === null) chatActions?.reset()
                else void chatActions?.loadSession(id)
              }}
              onNewSession={() => chatActions?.reset()}
              onOpenFile={handleOpenFile}
              onNavigate={onNavigate}
            />
          </div>
        </aside>

        {/* 中栏 */}
        <main className="flex-1 min-w-0 overflow-hidden">
          <ChatView
            workspaceId={workspace?.id}
            onNavigate={onNavigate}
            onOpenFile={handleOpenFile}
            ideMode
            onSessionChange={setSessionId}
            onStatsChange={setChatStats}
            onRegisterActions={setChatActions}
          />
        </main>

        {/* 右栏 */}
        <aside
          className={`ide-right border-l shrink-0 overflow-hidden ${rightOpen ? 'ide-panel-open-right' : 'ide-panel-closed'}`}
          style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-panel-bg)' }}
        >
          <div className="ide-right-inner h-full">
            <RightFilePanel
              path={openFile}
              workspaceId={workspace?.id}
              onClose={() => setOpenFile(null)}
            />
          </div>
        </aside>
      </div>
    </div>
  )
}
