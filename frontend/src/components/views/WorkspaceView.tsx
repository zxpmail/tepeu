import { useState, type Dispatch, type SetStateAction } from 'react'
import type { Workspace } from '../../types'

interface WorkspaceViewProps {
  workspace: {
    workspaces: Workspace[]
    current: Workspace | null
    setCurrent: Dispatch<SetStateAction<Workspace | null>>
    loading: boolean
    error: string | null
    createWorkspace: (name: string, desc?: string) => Promise<Workspace | null>
    deleteWorkspace: (id: string) => Promise<void>
  }
}

export default function WorkspaceView({ workspace }: WorkspaceViewProps) {
  const [showCreate, setShowCreate] = useState(false)
  const [name, setName] = useState('')
  const [desc, setDesc] = useState('')

  const handleCreate = async () => {
    if (!name.trim()) return
    await workspace.createWorkspace(name.trim(), desc.trim() || undefined)
    setName('')
    setDesc('')
    setShowCreate(false)
  }

  if (workspace.loading) {
    return (
      <div className="p-6" style={{ color: 'var(--color-text-secondary)' }}>
        加载工作区...
      </div>
    )
  }

  if (workspace.error) {
    return (
      <div className="p-6">
        <div className="p-4 rounded-lg border border-red-300 bg-red-50 dark:bg-red-900/20 dark:border-red-700 text-red-700 dark:text-red-300 text-sm">
          {workspace.error}
        </div>
      </div>
    )
  }

  return (
    <div className="p-6 max-w-2xl">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-lg font-semibold" style={{ color: 'var(--color-text)' }}>工作区</h1>
        <button
          onClick={() => setShowCreate(!showCreate)}
          className="px-3 py-1.5 text-sm rounded-lg transition-colors"
          style={{
            backgroundColor: 'var(--color-accent)',
            color: '#ffffff',
          }}
        >
          + 新建
        </button>
      </div>

      {showCreate && (
        <div className="mb-4 p-4 rounded-lg border" style={{
          borderColor: 'var(--color-border)',
          backgroundColor: 'var(--color-bg-secondary)',
        }}>
          <input
            placeholder="工作区名称"
            value={name}
            onChange={e => setName(e.target.value)}
            className="w-full px-3 py-2 mb-2 text-sm rounded border"
            style={{
              borderColor: 'var(--color-border)',
              backgroundColor: 'var(--color-bg)',
              color: 'var(--color-text)',
            }}
            autoFocus
          />
          <input
            placeholder="描述（可选）"
            value={desc}
            onChange={e => setDesc(e.target.value)}
            className="w-full px-3 py-2 mb-3 text-sm rounded border"
            style={{
              borderColor: 'var(--color-border)',
              backgroundColor: 'var(--color-bg)',
              color: 'var(--color-text)',
            }}
          />
          <div className="flex gap-2">
            <button onClick={handleCreate} className="px-3 py-1.5 text-sm rounded" style={{
              backgroundColor: 'var(--color-accent)', color: '#fff',
            }}>
              创建
            </button>
            <button onClick={() => setShowCreate(false)} className="px-3 py-1.5 text-sm rounded" style={{
              color: 'var(--color-text-secondary)',
            }}>
              取消
            </button>
          </div>
        </div>
      )}

      {/* Empty state */}
      {workspace.workspaces.length === 0 && !showCreate && (
        <div className="p-8 text-center" style={{ color: 'var(--color-text-secondary)' }}>
          <p className="mb-2">暂无工作区</p>
          <p className="text-sm">创建第一个工作区开始使用。</p>
        </div>
      )}

      {/* Workspace list */}
      <div className="space-y-1">
        {workspace.workspaces.map(ws => (
          <div
            key={ws.id}
            onClick={() => workspace.setCurrent(ws)}
            className="group flex items-center justify-between px-3 py-2 rounded cursor-pointer transition-colors"
            style={{
              backgroundColor: workspace.current?.id === ws.id ? 'var(--color-bg-tertiary)' : 'transparent',
              color: 'var(--color-text)',
            }}
          >
            <div>
              <div className="text-sm font-medium">{ws.name}</div>
              {ws.description && (
                <div className="text-xs mt-0.5" style={{ color: 'var(--color-text-secondary)' }}>{ws.description}</div>
              )}
            </div>
            <button
              onClick={e => { e.stopPropagation(); workspace.deleteWorkspace(ws.id) }}
              className="text-xs px-2 py-1 rounded opacity-0 group-hover:opacity-100 transition-opacity"
              style={{ color: 'var(--color-text-secondary)' }}
            >
              ✕
            </button>
          </div>
        ))}
      </div>
    </div>
  )
}
