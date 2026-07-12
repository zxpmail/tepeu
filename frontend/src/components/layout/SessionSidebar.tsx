/**
 * IDE 左栏 — 会话列表（可重命名/删除）+ 文件树 + 次级入口。
 * 关联：IdeShell、api.listSessions / renameSession / deleteSession、api.listFiles。
 */
import { useEffect, useState, useCallback, useRef, type KeyboardEvent, type ReactNode } from 'react'
import { api } from '../../api/client'
import { workspaceEventBus } from '../../context/WorkspaceEvents'
import type { ChatSession, FileItem, Panel } from '../../types'

interface SessionSidebarProps {
  workspaceId?: string
  workspaceName?: string
  sessionId?: string
  onSelectSession: (id: string | null) => void
  onNewSession: () => void
  onOpenFile: (path: string) => void
  onNavigate: (panel: Panel) => void
}

const SECONDARY: { id: Panel; label: string }[] = [
  { id: 'workspace', label: '工作区' },
  { id: 'memory', label: '记忆' },
  { id: 'skills', label: '技能' },
  { id: 'terminal', label: '终端' },
  { id: 'provider', label: '服务商' },
]

/** 按扩展名给文件列表一个轻量图标前缀 */
function fileIcon(name: string): string {
  const n = name.toLowerCase()
  if (/\.(html?|htm)$/.test(n)) return '🌐 '
  if (/\.pdf$/.test(n)) return '📕 '
  if (/\.(pptx?|ppt)$/.test(n)) return '📊 '
  if (/\.(png|jpe?g|gif|webp|svg)$/.test(n)) return '🖼 '
  if (/\.(md|markdown)$/.test(n)) return '📝 '
  return '📄 '
}

/** 侧栏小图标按钮用的 14px SVG */
function IconBtn({
  title,
  disabled,
  onClick,
  danger,
  children,
}: {
  title: string
  disabled?: boolean
  onClick: () => void
  danger?: boolean
  children: ReactNode
}) {
  return (
    <button
      type="button"
      title={title}
      disabled={disabled}
      onClick={(e) => { e.stopPropagation(); onClick() }}
      className="session-icon-btn"
      style={{ color: danger ? 'var(--color-danger)' : 'var(--color-text-dim)' }}
    >
      {children}
    </button>
  )
}

function IconPencil() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M12 20h9" />
      <path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z" />
    </svg>
  )
}

function IconTrash() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M3 6h18" />
      <path d="M8 6V4h8v2" />
      <path d="M19 6l-1 14H6L5 6" />
      <path d="M10 11v6M14 11v6" />
    </svg>
  )
}

function IconCheck() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M20 6 9 17l-5-5" />
    </svg>
  )
}

function IconX() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden>
      <path d="M18 6 6 18M6 6l12 12" />
    </svg>
  )
}

export default function SessionSidebar({
  workspaceId,
  workspaceName,
  sessionId,
  onSelectSession,
  onNewSession,
  onOpenFile,
  onNavigate,
}: SessionSidebarProps) {
  const [sessions, setSessions] = useState<ChatSession[]>([])
  const [files, setFiles] = useState<FileItem[]>([])
  const [filePath, setFilePath] = useState('/')
  const [explorerOpen, setExplorerOpen] = useState(true)
  const [loadingFiles, setLoadingFiles] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editTitle, setEditTitle] = useState('')
  const [busyId, setBusyId] = useState<string | null>(null)
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const filePathRef = useRef(filePath)
  filePathRef.current = filePath

  const reloadSessions = useCallback(() => {
    if (!workspaceId) {
      setSessions([])
      return
    }
    api.listSessions(workspaceId).then(setSessions).catch(() => setSessions([]))
  }, [workspaceId])

  const loadFiles = useCallback(async (path: string) => {
    setLoadingFiles(true)
    try {
      const data = await api.listFiles(path, workspaceId)
      setFiles(data.items)
      setFilePath(data.path)
    } catch {
      setFiles([])
    } finally {
      setLoadingFiles(false)
    }
  }, [workspaceId])

  /** 局部刷新当前文件目录（不刷新整页） */
  const refreshFiles = useCallback(() => {
    setExplorerOpen(true)
    void loadFiles(filePathRef.current)
  }, [loadFiles])

  useEffect(() => { reloadSessions() }, [reloadSessions, sessionId])
  useEffect(() => { void loadFiles('/') }, [loadFiles])

  // 对话写文件 / 回合结束 → 只刷新文件列表
  useEffect(() => {
    return workspaceEventBus.subscribe(() => {
      refreshFiles()
    })
  }, [refreshFiles])

  const joinPath = (base: string, name: string) => {
    if (base === '/' || base === '') return `/${name}`
    return `${base.replace(/\/$/, '')}/${name}`
  }

  const parentPath = (path: string) => {
    if (path === '/' || !path) return '/'
    const parts = path.replace(/\/$/, '').split('/')
    parts.pop()
    return parts.length <= 1 ? '/' : parts.join('/')
  }

  const startRename = (s: ChatSession) => {
    setConfirmDeleteId(null)
    setActionError(null)
    setEditingId(s.id)
    setEditTitle(s.title || '')
  }

  const cancelRename = () => {
    setEditingId(null)
    setEditTitle('')
  }

  const commitRename = async () => {
    if (!editingId) return
    const title = editTitle.trim() || '新对话'
    setBusyId(editingId)
    setActionError(null)
    try {
      const updated = await api.renameSession(editingId, title)
      setSessions(prev => prev.map(s => (s.id === updated.id ? { ...s, title: updated.title } : s)))
      cancelRename()
    } catch (e) {
      console.error(e)
      setActionError(e instanceof Error ? e.message : '重命名失败')
    } finally {
      setBusyId(null)
    }
  }

  const handleRenameKey = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      void commitRename()
    } else if (e.key === 'Escape') {
      e.preventDefault()
      cancelRename()
    }
  }

  const requestDelete = (s: ChatSession) => {
    setEditingId(null)
    setActionError(null)
    setConfirmDeleteId(s.id)
  }

  const cancelDelete = () => setConfirmDeleteId(null)

  const confirmDelete = async (s: ChatSession) => {
    setBusyId(s.id)
    setActionError(null)
    try {
      await api.deleteSession(s.id)
      setSessions(prev => prev.filter(x => x.id !== s.id))
      setConfirmDeleteId(null)
      if (sessionId === s.id) onNewSession()
    } catch (e) {
      console.error(e)
      setActionError(e instanceof Error ? e.message : '删除失败')
      setConfirmDeleteId(null)
    } finally {
      setBusyId(null)
    }
  }

  return (
    <div className="ide-sidebar h-full flex flex-col overflow-hidden">
      <div className="shrink-0 px-3 pt-3 pb-2">
        <div className="flex items-center justify-between gap-2 mb-2">
          <span className="text-sm font-semibold truncate" style={{ color: 'var(--color-text)' }}>
            Tepeu
          </span>
          <button
            type="button"
            className="text-xs px-2 py-1 rounded border shrink-0"
            style={{
              borderColor: 'var(--color-border)',
              color: 'var(--color-text)',
              backgroundColor: 'var(--color-bg)',
            }}
            onClick={onNewSession}
          >
            + 新建
          </button>
        </div>
        <div className="text-[11px] truncate" style={{ color: 'var(--color-text-dim)' }} title={workspaceName}>
          {workspaceName || '未选择工作区'}
        </div>
      </div>

      <div className="flex-1 min-h-0 overflow-y-auto px-2 pb-2">
        <div className="text-[10px] uppercase tracking-wide px-1 mb-1" style={{ color: 'var(--color-text-dim)' }}>
          会话
        </div>
        {!workspaceId && (
          <div className="text-xs px-2 py-2" style={{ color: 'var(--color-text-dim)' }}>请先创建工作区</div>
        )}
        {actionError && (
          <div className="text-[11px] px-2 py-1 mb-1 rounded" style={{ color: 'var(--color-danger)', backgroundColor: 'color-mix(in srgb, var(--color-danger) 12%, transparent)' }}>
            {actionError}
            <button type="button" className="ml-2 underline opacity-70" onClick={() => setActionError(null)}>关闭</button>
          </div>
        )}
        {sessions.map(s => {
          const active = s.id === sessionId
          const editing = editingId === s.id
          const busy = busyId === s.id
          const confirming = confirmDeleteId === s.id
          return (
            <div
              key={s.id}
              className="group flex items-center gap-0.5 rounded mb-0.5 px-1"
              style={{
                backgroundColor: confirming
                  ? 'color-mix(in srgb, var(--color-danger) 10%, transparent)'
                  : active
                    ? 'var(--color-bg-selected)'
                    : 'transparent',
              }}
            >
              {editing ? (
                <input
                  autoFocus
                  value={editTitle}
                  disabled={busy}
                  onChange={e => setEditTitle(e.target.value)}
                  onKeyDown={handleRenameKey}
                  onBlur={() => void commitRename()}
                  className="flex-1 min-w-0 text-xs px-1 py-1 rounded border outline-none"
                  style={{
                    borderColor: 'var(--color-accent)',
                    backgroundColor: 'var(--color-bg)',
                    color: 'var(--color-text)',
                  }}
                />
              ) : confirming ? (
                <>
                  <span
                    className="flex-1 min-w-0 text-xs px-1 py-1.5 truncate"
                    style={{ color: 'var(--color-danger)' }}
                  >
                    删除此对话？
                  </span>
                  <div className="shrink-0 flex items-center">
                    <IconBtn title="确认删除" disabled={busy} danger onClick={() => void confirmDelete(s)}>
                      <IconCheck />
                    </IconBtn>
                    <IconBtn title="取消" disabled={busy} onClick={cancelDelete}>
                      <IconX />
                    </IconBtn>
                  </div>
                </>
              ) : (
                <>
                  <button
                    type="button"
                    className="flex-1 min-w-0 text-left px-1 py-1.5 text-xs truncate"
                    style={{ color: active ? 'var(--color-text)' : 'var(--color-sidebar-text)' }}
                    onClick={() => { setConfirmDeleteId(null); onSelectSession(s.id) }}
                    onDoubleClick={() => startRename(s)}
                    title={s.title || s.id}
                    disabled={busy}
                  >
                    {s.title || s.id.slice(0, 8)}
                  </button>
                  <div className="shrink-0 flex items-center opacity-0 group-hover:opacity-100 focus-within:opacity-100 transition-opacity">
                    <IconBtn title="重命名" disabled={busy} onClick={() => startRename(s)}>
                      <IconPencil />
                    </IconBtn>
                    <IconBtn title="删除" disabled={busy} danger onClick={() => requestDelete(s)}>
                      <IconTrash />
                    </IconBtn>
                  </div>
                </>
              )}
            </div>
          )
        })}
        {workspaceId && sessions.length === 0 && (
          <div className="text-xs px-2 py-1" style={{ color: 'var(--color-text-dim)' }}>暂无会话</div>
        )}
      </div>

      <div
        className="shrink-0 border-t flex flex-col"
        style={{
          borderColor: 'var(--color-border)',
          maxHeight: explorerOpen ? '42%' : 36,
          minHeight: 36,
        }}
      >
        <button
          type="button"
          className="h-9 px-3 flex items-center gap-2 text-xs shrink-0 w-full"
          style={{ color: 'var(--color-text-secondary)' }}
          onClick={() => setExplorerOpen(o => !o)}
        >
          <span className="flex-1 text-left">文件</span>
          <span
            role="button"
            tabIndex={0}
            title="刷新文件列表"
            className="px-1.5 py-0.5 rounded text-[11px]"
            style={{ color: 'var(--color-text-dim)' }}
            onClick={(e) => {
              e.stopPropagation()
              refreshFiles()
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault()
                e.stopPropagation()
                refreshFiles()
              }
            }}
          >
            ↻
          </span>
          <span className="opacity-60">{explorerOpen ? '▼' : '▶'}</span>
        </button>
        {explorerOpen && (
          <div className="flex-1 overflow-y-auto px-1 pb-2 min-h-0">
            <div className="flex items-center gap-1 px-1 mb-1">
              <button
                type="button"
                className="text-[10px] px-1 rounded"
                style={{ color: 'var(--color-text-dim)' }}
                disabled={filePath === '/'}
                onClick={() => void loadFiles(parentPath(filePath))}
              >
                ↑
              </button>
              <span className="text-[10px] truncate flex-1" style={{ color: 'var(--color-text-dim)', fontFamily: 'var(--font-mono)' }}>
                {filePath}
              </span>
              {loadingFiles && (
                <span className="text-[10px]" style={{ color: 'var(--color-text-dim)' }}>…</span>
              )}
            </div>
            {files.map(f => {
              const full = joinPath(filePath, f.name)
              return (
                <button
                  key={full}
                  type="button"
                  className="w-full text-left px-2 py-1 rounded text-xs truncate"
                  style={{ color: 'var(--color-sidebar-text)', fontFamily: 'var(--font-mono)' }}
                  onClick={() => {
                    if (f.isDirectory) void loadFiles(full)
                    else onOpenFile(full)
                  }}
                >
                  {f.isDirectory ? '📁 ' : fileIcon(f.name)}{f.name}
                </button>
              )
            })}
          </div>
        )}
      </div>

      <div
        className="shrink-0 border-t grid grid-cols-2 gap-0.5 p-1.5"
        style={{ borderColor: 'var(--color-border)' }}
      >
        {SECONDARY.map(item => (
          <button
            key={item.id}
            type="button"
            className="text-[11px] py-1.5 rounded"
            style={{ color: 'var(--color-text-secondary)' }}
            onClick={() => onNavigate(item.id)}
          >
            {item.label}
          </button>
        ))}
      </div>
    </div>
  )
}
