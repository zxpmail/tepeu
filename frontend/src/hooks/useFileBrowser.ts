/**
 * 文件浏览器列表状态 — 按工作区隔离。
 * 关联：FileBrowserView、App、api.listFiles。
 */
import { useState, useCallback, useEffect } from 'react'
import { api } from '../api/client'
import type { FileItem } from '../types'

export function useFileBrowser(workspaceId?: string) {
  const [files, setFiles] = useState<FileItem[]>([])
  const [currentPath, setCurrentPath] = useState('/')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadFiles = useCallback(async (path: string = '/') => {
    setLoading(true)
    setError(null)
    try {
      const data = await api.listFiles(path, workspaceId)
      setFiles(data.items)
      setCurrentPath(data.path)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load files')
    } finally {
      setLoading(false)
    }
  }, [workspaceId])

  const navigateTo = useCallback((path: string) => {
    void loadFiles(path)
  }, [loadFiles])

  // 工作区切换或首次进入时重新加载根目录
  useEffect(() => {
    void loadFiles('/')
  }, [loadFiles])

  return { files, currentPath, loading, error, loadFiles, navigateTo }
}
