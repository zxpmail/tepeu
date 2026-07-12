import { useState, useEffect } from 'react'
import { useTheme } from './hooks/useTheme'
import { useWorkspace } from './hooks/useWorkspace'
import { useFileBrowser } from './hooks/useFileBrowser'
import { api } from './api/client'
import { WorkspaceEventsProvider } from './context/WorkspaceEvents'
import IdeShell from './components/layout/IdeShell'
import WorkspaceView from './components/views/WorkspaceView'
import FileBrowserView from './components/views/FileBrowserView'
import MemoryView from './components/views/MemoryView'
import TerminalView from './components/views/TerminalView'
import ProviderSettingsView from './components/views/ProviderSettingsView'
import SkillsView from './components/views/SkillsView'
import SetupWizard from './components/views/SetupWizard'
import ThemeToggle from './components/common/ThemeToggle'
import type { Panel } from './types'

/** 次级全屏面板（非 IDE 主路径） */
const SECONDARY_PANELS: Panel[] = ['workspace', 'files', 'memory', 'terminal', 'provider', 'skills']

export default function App() {
  const { theme, setTheme } = useTheme()
  const workspace = useWorkspace()
  const fileBrowser = useFileBrowser()
  const [activePanel, setActivePanel] = useState<Panel>('chat')
  const [showSetup, setShowSetup] = useState<boolean | null>(null)

  useEffect(() => {
    api.getAvailableProviders()
      .then(async (providers) => {
        for (const p of providers) {
          try {
            const cfg = await api.getProviderConfig(p.id)
            if (cfg?.apiKey && cfg?.enabled) {
              setShowSetup(false)
              return
            }
          } catch { /* not configured */ }
        }
        setShowSetup(true)
      })
      .catch(() => setShowSetup(true))
  }, [])

  const handleSetupComplete = () => setShowSetup(false)

  if (showSetup === null) {
    return (
      <div className="h-screen flex items-center justify-center" style={{ backgroundColor: 'var(--color-bg)' }}>
        <div className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>加载中…</div>
      </div>
    )
  }

  if (showSetup) {
    return <SetupWizard onComplete={handleSetupComplete} />
  }

  const isSecondary = SECONDARY_PANELS.includes(activePanel)

  const renderSecondary = () => {
    switch (activePanel) {
      case 'workspace':
        return <WorkspaceView workspace={workspace} />
      case 'files':
        return <FileBrowserView fileBrowser={fileBrowser} workspaceId={workspace.current?.id} />
      case 'memory':
        return <MemoryView workspaceId={workspace.current?.id} />
      case 'terminal':
        return <TerminalView />
      case 'provider':
        return <ProviderSettingsView />
      case 'skills':
        return <SkillsView workspaceId={workspace.current?.id} />
      default:
        return null
    }
  }

  return (
    <WorkspaceEventsProvider>
      <div className="h-screen flex flex-col overflow-hidden" style={{ backgroundColor: 'var(--color-bg)' }}>
        {isSecondary ? (
          <>
            <header
              className="h-10 flex items-center px-3 border-b shrink-0 select-none"
              style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg-secondary)' }}
            >
              <button
                type="button"
                className="text-xs px-2 py-1 rounded mr-2"
                style={{ color: 'var(--color-accent)' }}
                onClick={() => setActivePanel('chat')}
              >
                ← 返回对话
              </button>
              <span className="font-semibold text-sm" style={{ color: 'var(--color-text)' }}>Tepeu</span>
              <span className="ml-2 text-xs" style={{ color: 'var(--color-text-secondary)' }}>
                {activePanel === 'workspace' && '工作区'}
                {activePanel === 'files' && '文件'}
                {activePanel === 'memory' && '记忆'}
                {activePanel === 'skills' && '技能'}
                {activePanel === 'terminal' && '终端'}
                {activePanel === 'provider' && '服务商'}
              </span>
              <div className="ml-auto">
                <ThemeToggle theme={theme} onToggle={setTheme} />
              </div>
            </header>
            <main className="flex-1 overflow-auto">
              {renderSecondary()}
            </main>
          </>
        ) : (
          <IdeShell
            workspace={workspace.current}
            theme={theme}
            onToggleTheme={setTheme}
            onNavigate={setActivePanel}
          />
        )}
      </div>
    </WorkspaceEventsProvider>
  )
}
