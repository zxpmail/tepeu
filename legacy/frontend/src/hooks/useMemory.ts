import { useState, useCallback, useRef } from 'react'
import { api } from '../api/client'
import type { Memory } from '../types'

export interface UseMemoryReturn {
  memories: Memory[]
  loading: boolean
  error: string | null
  searchQuery: string
  tags: string[]
  hasMore: boolean
  setSearchQuery: (q: string) => void
  setTags: (t: string[]) => void
  loadMemories: (reset?: boolean) => void
  createMemory: (workspaceId: string, content: string, tags?: string[]) => Promise<Memory | null>
  updateMemory: (id: string, content: string, tags?: string[]) => Promise<void>
  deleteMemory: (id: string) => Promise<boolean>
}

export function useMemory(workspaceId: string | undefined): UseMemoryReturn {
  const [memories, setMemories] = useState<Memory[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [searchQuery, setSearchQuery] = useState('')
  const [tags, setTags] = useState<string[]>([])
  const [hasMore, setHasMore] = useState(false)
  const cursorRef = useRef<string | undefined>(undefined)

  const loadMemories = useCallback((reset: boolean = false) => {
    if (!workspaceId) return
    if (reset) {
      cursorRef.current = undefined
      setMemories([])
    }
    setLoading(true)
    setError(null)

    api.searchMemories({
      workspaceId,
      query: searchQuery || undefined,
      tags: tags.length > 0 ? tags : undefined,
      limit: 20,
      cursor: reset ? undefined : cursorRef.current,
    })
      .then(data => {
        if (reset) {
          setMemories(data.items)
        } else {
          setMemories(prev => [...prev, ...data.items])
        }
        setHasMore(data.hasMore)
        cursorRef.current = data.nextCursor
      })
      .catch(e => setError(e instanceof Error ? e.message : 'Failed to load memories'))
      .finally(() => setLoading(false))
  }, [workspaceId, searchQuery, tags])

  const createMemory = useCallback(async (wid: string, content: string, memoryTags?: string[]): Promise<Memory | null> => {
    try {
      const m = await api.createMemory(wid, content, 'manual', memoryTags)
      setMemories(prev => [m, ...prev])
      return m
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to create memory')
      return null
    }
  }, [])

  const updateMemory = useCallback(async (id: string, content: string, memoryTags?: string[]) => {
    try {
      const updated = await api.updateMemory(id, content, memoryTags)
      setMemories(prev => prev.map(m => m.id === id ? { ...m, ...updated } : m))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to update memory')
    }
  }, [])

  const deleteMemory = useCallback(async (id: string): Promise<boolean> => {
    try {
      await api.deleteMemory(id)
      setMemories(prev => prev.filter(m => m.id !== id))
      return true
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to delete memory')
      return false
    }
  }, [])

  return {
    memories, loading, error, searchQuery, tags, hasMore,
    setSearchQuery, setTags,
    loadMemories, createMemory, updateMemory, deleteMemory,
  }
}
