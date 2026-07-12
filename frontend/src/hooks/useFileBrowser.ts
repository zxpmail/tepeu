import { useState, useCallback, useEffect, useRef } from 'react'
import { api } from '../api/client'
import type { FileItem } from '../types'

export function useFileBrowser() {
  const [files, setFiles] = useState<FileItem[]>([])
  const [currentPath, setCurrentPath] = useState('/')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadFiles = useCallback(async (path: string = '/') => {
    setLoading(true)
    setError(null)
    try {
      const data = await api.listFiles(path)
      setFiles(data.items)
      setCurrentPath(data.path)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load files')
    } finally {
      setLoading(false)
    }
  }, [])

  const navigateTo = useCallback((path: string) => {
    loadFiles(path)
  }, [loadFiles])

  // Load the root listing exactly once. State is hoisted into App, so it persists across
  // panel switches — FileBrowserView remounts do NOT reset the user's directory (fixes M12).
  const loadedRef = useRef(false)
  useEffect(() => {
    if (!loadedRef.current) {
      loadedRef.current = true
      loadFiles('/')
    }
  }, [loadFiles])

  return { files, currentPath, loading, error, loadFiles, navigateTo }
}
