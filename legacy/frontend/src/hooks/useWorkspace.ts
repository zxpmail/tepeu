import { useState, useEffect, useCallback } from 'react'
import { api } from '../api/client'
import type { Workspace } from '../types'

/**
 * 工作区列表与当前选中；切换走 POST /switch 校验后再落本地状态。
 */
export function useWorkspace() {
  const [workspaces, setWorkspaces] = useState<Workspace[]>([])
  const [current, setCurrent] = useState<Workspace | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // 无 current 依赖：setCurrent 用函数式更新，load 只触发一次
  const loadWorkspaces = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await api.listWorkspaces()
      setWorkspaces(data)
      setCurrent(prev => prev ?? data[0] ?? null)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load workspaces')
    } finally {
      setLoading(false)
    }
  }, [])

  const createWorkspace = useCallback(async (name: string, description?: string) => {
    setError(null)
    try {
      const ws = await api.createWorkspace(name, description)
      setWorkspaces(prev => [...prev, ws])
      setCurrent(ws)
      return ws
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create workspace')
      return null
    }
  }, [])

  const deleteWorkspace = useCallback(async (id: string) => {
    setError(null)
    try {
      await api.deleteWorkspace(id)
      setWorkspaces(prev => prev.filter(w => w.id !== id))
      setCurrent(prev => (prev?.id === id ? null : prev))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to delete workspace')
    }
  }, [])

  /** 先调后端 switch 校验存在，再更新当前工作区 */
  const switchWorkspace = useCallback(async (id: string) => {
    setError(null)
    try {
      const ws = await api.switchWorkspace(id)
      setCurrent(ws)
      return ws
    } catch (e) {
      setError(e instanceof Error ? e.message : '切换工作区失败')
      return null
    }
  }, [])

  useEffect(() => { loadWorkspaces() }, [loadWorkspaces])

  return {
    workspaces,
    current,
    setCurrent,
    switchWorkspace,
    loading,
    error,
    createWorkspace,
    deleteWorkspace,
    reload: loadWorkspaces,
  }
}
