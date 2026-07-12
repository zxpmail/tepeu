/**
 * SSE 对话消费 hook — 支持排队发送、usage 统计、file_changed 广播、会话加载。
 * 关联：api/client、WorkspaceEvents.workspaceEventBus。
 */
import { useState, useCallback, useRef } from 'react'
import { api } from '../api/client'
import { workspaceEventBus } from '../context/WorkspaceEvents'
import type { SessionStats } from '../types'

/** 单轮 usage（来自 SSE usage 事件） */
export interface LastUsage {
  promptTokens: number
  completionTokens: number
  totalTokens: number
  costUsd: number
}

export interface ChatMessage {
  id?: string
  role: 'user' | 'assistant' | 'tool'
  content: string
  streaming?: boolean
  tool?: string
  toolKind?: 'call' | 'result'
  /** 本轮完成后挂到助手消息上的用量 */
  usage?: LastUsage
}

type SseEvent =
  | { type: 'token'; content: string }
  | { type: 'tool_call'; tool: string; params: unknown }
  | { type: 'tool_result'; tool: string; content: string }
  | { type: 'usage'; promptTokens?: number; completionTokens?: number; totalTokens?: number; costUsd?: number }
  | { type: 'file_changed'; path: string; operation?: string }
  | { type: 'final' }
  | { type: 'error'; code?: string; message?: string }

interface PendingItem {
  text: string
  workspaceId: string
  provider: string
  fileRefs?: string[]
}

/** 从文本中解析 @path 引用 */
function parseFileRefs(text: string): string[] {
  const refs: string[] = []
  const re = /@([^\s]+)/g
  let m: RegExpExecArray | null
  while ((m = re.exec(text)) !== null) {
    if (m[1]) refs.push(m[1])
  }
  return refs
}

/**
 * SSE chat consumer. The backend `POST /api/chat/stream` is a POST endpoint, so browser
 * `EventSource` (GET-only) can't be used — we stream the Response body and parse the
 * `event: message` / `data: {...}` frames manually.
 */
export function useChat() {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [streaming, setStreaming] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [sessionId, setSessionId] = useState<string | undefined>(undefined)
  const [lastUsage, setLastUsage] = useState<LastUsage | null>(null)
  const [sessionStats, setSessionStats] = useState<SessionStats | null>(null)
  const [queueLength, setQueueLength] = useState(0)
  const abortRef = useRef<AbortController | null>(null)
  const pendingQueue = useRef<PendingItem[]>([])
  const streamingRef = useRef(false)
  const sessionIdRef = useRef<string | undefined>(undefined)
  const sendRef = useRef<(
    text: string,
    workspaceId: string | undefined,
    provider: string,
    fileRefs?: string[],
  ) => Promise<void>>(async () => {})

  /** 同步 sessionId / streaming 到 ref，供队列续发读取最新值 */
  const syncSessionId = (id: string | undefined) => {
    sessionIdRef.current = id
    setSessionId(id)
  }

  const setStreamingBoth = (v: boolean) => {
    streamingRef.current = v
    setStreaming(v)
  }

  const reset = useCallback(() => {
    abortRef.current?.abort()
    pendingQueue.current = []
    setQueueLength(0)
    setMessages([])
    setError(null)
    syncSessionId(undefined)
    setLastUsage(null)
    setSessionStats(null)
    setStreamingBoth(false)
  }, [])

  const stop = useCallback(() => { abortRef.current?.abort() }, [])

  /** 刷新会话统计 */
  const refreshStats = useCallback(async (sid: string) => {
    try {
      const stats = await api.getSessionStats(sid)
      setSessionStats(stats)
    } catch {
      /* 统计失败不阻断对话 */
    }
  }, [])

  const loadSession = useCallback(async (sid: string) => {
    setError(null)
    setStreamingBoth(false)
    abortRef.current?.abort()
    pendingQueue.current = []
    setQueueLength(0)
    try {
      const s = await api.getSession(sid)
      const msgs = s.messages ?? []
      setMessages(
        msgs
          .filter(m => m.role !== 'system')
          .map(m => ({
            id: m.id,
            role: m.role as 'user' | 'assistant',
            content: m.content,
          })),
      )
      syncSessionId(sid)
      void refreshStats(sid)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load session')
    }
  }, [refreshStats])

  /** 实际发送（不经队列门闩） */
  const doSend = useCallback(async (
    text: string,
    workspaceId: string,
    provider: string,
    fileRefs?: string[],
  ) => {
    const trimmed = text.trim()
    if (!trimmed) return

    const refs = fileRefs ?? parseFileRefs(trimmed)
    setError(null)
    setMessages(prev => [...prev, { role: 'user', content: trimmed }])
    setStreamingBoth(true)
    const controller = new AbortController()
    abortRef.current = controller

    const appendToken = (chunk: string) =>
      setMessages(prev => {
        const last = prev[prev.length - 1]
        if (last && last.role === 'assistant' && last.streaming) {
          const next = [...prev]
          next[next.length - 1] = { ...last, content: last.content + chunk }
          return next
        }
        return [...prev, { role: 'assistant', content: chunk, streaming: true }]
      })

    const pushTool = (m: ChatMessage) => setMessages(prev => [...prev, m])

    try {
      let sid = sessionIdRef.current
      if (!sid) {
        const session = await api.createSession(workspaceId, trimmed.slice(0, 40))
        sid = session.id
        syncSessionId(sid)
      }

      const body: Record<string, unknown> = {
        message: trimmed,
        workspaceId,
        sessionId: sid,
        provider,
      }
      if (refs.length > 0) body.fileRefs = refs

      const res = await fetch('/api/chat/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
        signal: controller.signal,
      })

      if (!res.ok || !res.body) {
        let msg = `HTTP ${res.status}`
        try { const b = await res.json(); msg = b?.message || msg } catch { /* keep status */ }
        throw new Error(msg)
      }

      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })

        let sep: number
        while ((sep = buffer.indexOf('\n\n')) >= 0) {
          const frame = buffer.slice(0, sep)
          buffer = buffer.slice(sep + 2)
          const dataLine = frame.split('\n').find(l => l.startsWith('data:'))
          if (!dataLine) continue
          const json = dataLine.slice(5).trim()
          if (!json) continue
          let evt: SseEvent
          try { evt = JSON.parse(json) } catch { continue }

          if (evt.type === 'token' && evt.content) {
            appendToken(evt.content)
          } else if (evt.type === 'tool_call') {
            pushTool({ role: 'tool', tool: evt.tool, toolKind: 'call', content: JSON.stringify(evt.params) })
          } else if (evt.type === 'tool_result') {
            pushTool({ role: 'tool', tool: evt.tool, toolKind: 'result', content: evt.content })
          } else if (evt.type === 'usage') {
            const usage: LastUsage = {
              promptTokens: evt.promptTokens ?? 0,
              completionTokens: evt.completionTokens ?? 0,
              totalTokens: evt.totalTokens ?? 0,
              costUsd: evt.costUsd ?? 0,
            }
            setLastUsage(usage)
            setMessages(prev => {
              for (let i = prev.length - 1; i >= 0; i--) {
                if (prev[i]!.role === 'assistant') {
                  const next = [...prev]
                  next[i] = { ...prev[i]!, usage }
                  return next
                }
              }
              return prev
            })
          } else if (evt.type === 'file_changed' && evt.path) {
            workspaceEventBus.emitFileChanged(evt.path)
          } else if (evt.type === 'final') {
            // 回合结束再刷一次文件树，避免漏掉 file_changed
            workspaceEventBus.emitFileChanged('')
          } else if (evt.type === 'error') {
            throw new Error(evt.message || evt.code || 'Chat error')
          }
        }
      }
    } catch (e) {
      if ((e as Error)?.name === 'AbortError') return
      setError(e instanceof Error ? e.message : 'Chat failed')
    } finally {
      setMessages(prev => {
        const last = prev[prev.length - 1]
        if (last && last.role === 'assistant' && last.streaming) {
          const next = [...prev]
          next[next.length - 1] = { ...last, streaming: false }
          return next
        }
        return prev
      })
      setStreamingBoth(false)
      abortRef.current = null

      const sid = sessionIdRef.current
      if (sid) void refreshStats(sid)

      // 队列续发下一条
      const next = pendingQueue.current.shift()
      setQueueLength(pendingQueue.current.length)
      if (next) {
        void sendRef.current(next.text, next.workspaceId, next.provider, next.fileRefs)
      }
    }
  }, [refreshStats])

  const send = useCallback(async (
    text: string,
    workspaceId: string | undefined,
    provider: string,
    fileRefs?: string[],
  ) => {
    const trimmed = text.trim()
    if (!trimmed || !workspaceId || !provider) return

    // 流式中：入队，由父组件继续调用 send 实现「排队」
    if (streamingRef.current) {
      pendingQueue.current.push({ text: trimmed, workspaceId, provider, fileRefs })
      setQueueLength(pendingQueue.current.length)
      return
    }

    await doSend(trimmed, workspaceId, provider, fileRefs)
  }, [doSend])

  sendRef.current = send

  return {
    messages,
    streaming,
    error,
    sessionId,
    send,
    stop,
    reset,
    loadSession,
    setSessionId: syncSessionId,
    lastUsage,
    sessionStats,
    refreshStats,
    queueLength,
  }
}
