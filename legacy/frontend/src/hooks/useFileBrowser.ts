/**
 * 文件浏览器列表状态 — 按工作区隔离。
 * 关联：FileBrowserView、App、api.listFiles。
 * Phase 12：订阅 file_changed 事件，外部文件变更自动刷新当前目录。
 */
import { useState, useCallback, useEffect, useRef } from 'react'
import { api } from '../api/client'
import { workspaceEventBus } from '../context/WorkspaceEvents'
import type { FileItem } from '../types'

const REFRESH_DEBOUNCE_MS = 300

export function useFileBrowser(workspaceId?: string) {
  const [files, setFiles] = useState<FileItem[]>([])
  const [currentPath, setCurrentPath] = useState('/')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const currentPathRef = useRef(currentPath)
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  /** 请求序号：工作区切换时旧响应用序数淘汰，避免 stale 覆盖 */
  const requestSeq = useRef(0)

  const loadFiles = useCallback(async (path: string = '/') => {
    const seq = ++requestSeq.current
    setLoading(true)
    setError(null)
    try {
      const data = await api.listFiles(path, workspaceId)
      if (seq !== requestSeq.current) return // 旧工作区的慢响应不覆盖新状态
      setFiles(data.items)
      setCurrentPath(data.path)
      currentPathRef.current = data.path
    } catch (e) {
      if (seq !== requestSeq.current) return
      setError(e instanceof Error ? e.message : 'Failed to load files')
    } finally {
      if (seq === requestSeq.current) setLoading(false)
    }
  }, [workspaceId])

  const navigateTo = useCallback((path: string) => {
    void loadFiles(path)
  }, [loadFiles])

  // 工作区切换或首次进入时重新加载根目录
  useEffect(() => {
    void loadFiles('/')
  }, [loadFiles])

  // 订阅 file_changed：仅刷新当前目录子树，防抖合并连续事件（Phase 12）
  useEffect(() => {
    const unsubscribe = workspaceEventBus.subscribe((path, eventWorkspaceId) => {
      // 带 workspaceId 的事件必须匹配当前工作区；缺省视为当前工作区
      if (eventWorkspaceId != null && eventWorkspaceId !== workspaceId) return
      const current = currentPathRef.current
      const isWithin = path === ''
        || current === '/'
        || path === current
        || path.startsWith(current + '/')
      if (!isWithin) return
      if (debounceRef.current != null) clearTimeout(debounceRef.current)
      debounceRef.current = setTimeout(() => {
        void loadFiles(currentPathRef.current)
      }, REFRESH_DEBOUNCE_MS)
    })
    return () => {
      unsubscribe()
      if (debounceRef.current != null) clearTimeout(debounceRef.current)
    }
  }, [workspaceId, loadFiles])

  return { files, currentPath, loading, error, loadFiles, navigateTo }
}
