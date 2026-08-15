/**
 * 可展开目录树：按需加载子目录。
 * 关联：FileBrowserView、SessionSidebar、api.listFiles。
 */
import { useCallback, useEffect, useState } from 'react'
import { api } from '../../api/client'
import type { FileItem } from '../../types'

function joinPath(base: string, name: string): string {
  if (base === '/' || base === '') return `/${name}`
  return `${base.replace(/\/$/, '')}/${name}`
}

interface TreeNodeProps {
  path: string
  name: string
  workspaceId?: string
  depth: number
  selectedPath?: string
  onSelectDir: (path: string) => void
  onSelectFile?: (path: string) => void
  refreshKey?: number
}

function TreeNode({
  path,
  name,
  workspaceId,
  depth,
  selectedPath,
  onSelectDir,
  onSelectFile,
  refreshKey,
}: TreeNodeProps) {
  const [expanded, setExpanded] = useState(path === '/')
  const [children, setChildren] = useState<FileItem[] | null>(null)
  const [loading, setLoading] = useState(false)

  const loadChildren = useCallback(async () => {
    setLoading(true)
    try {
      const data = await api.listFiles(path, workspaceId)
      setChildren(data.items)
    } catch {
      setChildren([])
    } finally {
      setLoading(false)
    }
  }, [path, workspaceId])

  useEffect(() => {
    if (expanded) {
      void loadChildren()
    }
  }, [expanded, loadChildren, refreshKey])

  const selected = selectedPath === path
    || (selectedPath != null && selectedPath.startsWith(path === '/' ? '/' : path + '/'))

  return (
    <div>
      <button
        type="button"
        className="w-full flex items-center gap-0.5 text-left text-xs py-0.5 rounded truncate"
        style={{
          paddingLeft: 4 + depth * 12,
          color: selected ? 'var(--color-text)' : 'var(--color-sidebar-text)',
          backgroundColor: selectedPath === path
            ? 'var(--color-bg-selected)'
            : 'transparent',
          fontFamily: 'var(--font-mono)',
        }}
        onClick={() => {
          setExpanded(e => !e)
          onSelectDir(path)
        }}
        title={path}
      >
        <span className="w-3 shrink-0 opacity-60">{expanded ? '▼' : '▶'}</span>
        <span className="truncate">📁 {name}</span>
        {loading && <span className="ml-1 opacity-50">…</span>}
      </button>
      {expanded && children && (
        <div>
          {children
            .filter(c => c.isDirectory)
            .map(c => {
              const childPath = joinPath(path, c.name)
              return (
                <TreeNode
                  key={childPath}
                  path={childPath}
                  name={c.name}
                  workspaceId={workspaceId}
                  depth={depth + 1}
                  selectedPath={selectedPath}
                  onSelectDir={onSelectDir}
                  onSelectFile={onSelectFile}
                  refreshKey={refreshKey}
                />
              )
            })}
          {onSelectFile && children
            .filter(c => !c.isDirectory)
            .map(c => {
              const childPath = joinPath(path, c.name)
              return (
                <button
                  key={childPath}
                  type="button"
                  className="w-full text-left text-xs py-0.5 rounded truncate"
                  style={{
                    paddingLeft: 16 + (depth + 1) * 12,
                    color: selectedPath === childPath ? 'var(--color-text)' : 'var(--color-sidebar-text)',
                    backgroundColor: selectedPath === childPath
                      ? 'var(--color-bg-selected)'
                      : 'transparent',
                    fontFamily: 'var(--font-mono)',
                  }}
                  onClick={() => onSelectFile(childPath)}
                  title={childPath}
                >
                  📄 {c.name}
                </button>
              )
            })}
        </div>
      )}
    </div>
  )
}

export interface FileTreeProps {
  workspaceId?: string
  selectedPath?: string
  onSelectDir: (path: string) => void
  onSelectFile?: (path: string) => void
  /** 变更时强制重新加载已展开节点 */
  refreshKey?: number
  className?: string
  showFiles?: boolean
}

export default function FileTree({
  workspaceId,
  selectedPath,
  onSelectDir,
  onSelectFile,
  refreshKey = 0,
  className,
  showFiles = false,
}: FileTreeProps) {
  return (
    <div className={className ?? ''} data-testid="file-tree">
      <TreeNode
        path="/"
        name="~"
        workspaceId={workspaceId}
        depth={0}
        selectedPath={selectedPath}
        onSelectDir={onSelectDir}
        onSelectFile={showFiles ? onSelectFile : undefined}
        refreshKey={refreshKey}
      />
    </div>
  )
}
