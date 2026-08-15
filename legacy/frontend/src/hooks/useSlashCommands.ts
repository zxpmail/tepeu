/**
 * Slash 命令 — 拉目录、解析输入、调用后端（不经 LLM）。
 * 关联：ChatInput 候选、ChatView 发送拦截、/api/slash。
 */
import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '../api/client'

export interface SlashCatalogItem {
  name: string
  description: string
  usage: string
  requiresWorkspace: boolean
}

export interface SlashExecuteResult {
  command: string
  text: string
  action?: string
}

/** 解析 `/schedule list` → { name, args, line }；非 slash 返回 null */
export function parseSlashLine(raw: string): { name: string; args: string[]; line: string } | null {
  const line = raw.trim()
  if (!line.startsWith('/')) return null
  const body = line.slice(1).trim()
  if (!body) return { name: '', args: [], line }
  const parts = body.split(/\s+/)
  const name = (parts[0] || '').toLowerCase()
  const args = parts.slice(1)
  return { name, args, line }
}

function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}

export function useSlashCommands() {
  const [catalog, setCatalog] = useState<SlashCatalogItem[]>([])
  const [loading, setLoading] = useState(false)
  /** 首次目录加载是否完成（成功或失败）——目录未就绪时 slash 输入一律走后端，避免误送 LLM */
  const [ready, setReady] = useState(false)
  const [loadError, setLoadError] = useState<string | null>(null)

  const reload = useCallback(async () => {
    setLoading(true)
    setLoadError(null)
    let lastErr: unknown = null
    // 失败重试一次，降低瞬时网络抖动导致候选为空的概率
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        setCatalog(await api.listSlashCommands())
        setReady(true)
        setLoading(false)
        return
      } catch (e) {
        lastErr = e
        if (attempt === 0) await delay(400)
      }
    }
    setCatalog([])
    setLoadError(lastErr instanceof Error ? lastErr.message : '命令目录加载失败')
    setReady(true)
    setLoading(false)
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  const isSystemCommand = useCallback((name: string) => {
    const n = name.toLowerCase()
    return catalog.some(c => c.name === n)
  }, [catalog])

  const execute = useCallback(async (
    commandLine: string,
    workspaceId?: string,
    sessionId?: string,
  ): Promise<SlashExecuteResult> => {
    try {
      return await api.executeSlash(commandLine, workspaceId, sessionId)
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : (e instanceof Error ? e.message : '命令执行失败')
      throw new Error(msg)
    }
  }, [])

  return { catalog, loading, ready, loadError, reload, isSystemCommand, execute }
}
