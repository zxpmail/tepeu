import { useState, useEffect, useCallback } from 'react'
import { api } from '../api/client'
import type { Workspace } from '../types'

export function useWorkspace() {
  const [workspaces, setWorkspaces] = useState<Workspace[]>([])
  const [current, setCurrent] = useState<Workspace | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // No `current` in deps: setCurrent uses the functional form so this callback is stable,
  // and the load effect fires once (not on every workspace switch).
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

  useEffect(() => { loadWorkspaces() }, [loadWorkspaces])

  return { workspaces, current, setCurrent, loading, error, createWorkspace, deleteWorkspace, reload: loadWorkspaces }
}
