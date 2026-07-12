/**
 * IDE 三栏主壳 — 左会话/文件、中对话、右预览。
 * 关联：SessionSidebar、ChatView、RightFilePanel、App。
 */
import { useState, useCallback } from 'react'
import SessionSidebar from './SessionSidebar'
import RightFilePanel from '../files/RightFilePanel'
import ChatView from '../views/ChatView'
import ThemeToggle from '../common/ThemeToggle'
import type { Panel, Theme, Workspace } from '../../types'
import type { LastUsage } from '../../hooks/useChat'
import type { SessionStats } from '../../types'

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

  const handleOpenFile = useCallback((path: string) => {
    setOpenFile(path)
    setRightOpen(true)
  }, [])

  const prompt = chatStats.lastUsage?.promptTokens ?? 0
  const comp = chatStats.lastUsage?.completionTokens ?? 0
  const cost = chatStats.sessionStats?.totalCostUsd ?? chatStats.lastUsage?.costUsd ?? 0

  return (
    <div className="ide-shell h-full flex flex-col overflow-hidden">
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
          <span>↑{prompt}</span>
          <span>↓{comp}</span>
          <span>~${cost.toFixed(4)}</span>
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
