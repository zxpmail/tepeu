/**
 * SSE 对话消费 hook — 支持排队发送、usage 统计、file_changed 广播、会话加载、工具审批。
 * 关联：api/client、WorkspaceEvents.workspaceEventBus、ApprovalBanner。
 */
import { useState, useCallback, useRef } from 'react'
import { api } from '../api/client'
import { workspaceEventBus } from '../context/WorkspaceEvents'
import type { SessionStats } from '../types'
import type { PendingApproval } from '../components/chat/ApprovalBanner'

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
  toolKind?: string
  toolState?: 'call' | 'result'
  /** 本轮完成后挂到助手消息上的用量 */
  usage?: LastUsage
}

type SseEvent =
  | { type: 'token'; content: string }
  | { type: 'tool_call'; tool: string; toolKind?: string; params: unknown }
  | { type: 'tool_result'; tool: string; toolKind?: string; content: string }
  | { type: 'tool_approval_required'; approvalId: string; tool: string; toolKind?: string; params?: string; sessionId?: string }
  | { type: 'tool_denied'; tool: string; toolKind?: string; content?: string }
  | { type: 'usage'; promptTokens?: number; completionTokens?: number; totalTokens?: number; costUsd?: number }
  | { type: 'file_changed'; toolKind?: string; path: string; operation?: string }
  | { type: 'hallucination_warning'; message?: string; missingPaths?: string[] }
  | { type: 'final' }
  | { type: 'error'; code?: string; message?: string }

interface PendingItem {
  text: string
  workspaceId: string
  provider: string
  fileRefs?: string[]
}

const TOOL_TRACE_PREFIX = 'TEPEU_TOOL_V1:'

/** 从文本中解析 @path 引用（支持 @"path with spaces"） */
function parseFileRefs(text: string): string[] {
  const refs: string[] = []
  const quoted = /@["']([^"']+)["']/g
  let m: RegExpExecArray | null
  while ((m = quoted.exec(text)) !== null) {
    if (m[1]) refs.push(m[1])
  }
  const bare = /@([^\s"']+)/g
  while ((m = bare.exec(text)) !== null) {
    if (m[1] && !refs.includes(m[1])) refs.push(m[1])
  }
  return refs
}

/** 将持久化的工具 system 行还原为前端 tool 消息 */
function decodeToolTrace(content: string): ChatMessage | null {
  if (!content.startsWith(TOOL_TRACE_PREFIX)) return null
  try {
    const p = JSON.parse(content.slice(TOOL_TRACE_PREFIX.length)) as {
      tool?: string
      toolKind?: string
      toolState?: 'call' | 'result'
      content?: string
    }
    if (!p.tool) return null
    return {
      role: 'tool',
      tool: p.tool,
      toolKind: p.toolKind ?? 'unknown',
      toolState: p.toolState ?? 'result',
      content: p.content ?? '',
    }
  } catch {
    return null
  }
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
  const [pendingApprovals, setPendingApprovals] = useState<PendingApproval[]>([])
  const [decidingId, setDecidingId] = useState<string | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const pendingQueue = useRef<PendingItem[]>([])
  const streamingRef = useRef(false)
  const sessionIdRef = useRef<string | undefined>(undefined)
  /** 最近一次发送上下文，供批准后自动续跑 */
  const lastSendRef = useRef<{ workspaceId: string; provider: string } | null>(null)
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
    setPendingApprovals([])
    setDecidingId(null)
    lastSendRef.current = null
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
    setPendingApprovals([])
    setDecidingId(null)
    try {
      const s = await api.getSession(sid)
      const msgs = s.messages ?? []
      const mapped: ChatMessage[] = []
      for (const m of msgs) {
        if (m.role === 'system') {
          const tool = decodeToolTrace(m.content)
          if (tool) mapped.push({ ...tool, id: m.id })
          continue
        }
        if (m.role === 'user' || m.role === 'assistant') {
          mapped.push({ id: m.id, role: m.role, content: m.content })
        }
      }
      setMessages(mapped)
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
    lastSendRef.current = { workspaceId, provider }
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
        idempotencyKey: crypto.randomUUID(),
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
            pushTool({ role: 'tool', tool: evt.tool, toolKind: evt.toolKind ?? 'unknown', toolState: 'call', content: JSON.stringify(evt.params) })
          } else if (evt.type === 'tool_result') {
            pushTool({ role: 'tool', tool: evt.tool, toolKind: evt.toolKind ?? 'unknown', toolState: 'result', content: evt.content })
            // 超时/拒绝后清掉对应审批条，避免 UI 悬挂
            if (typeof evt.content === 'string'
                && (evt.content.includes('APPROVAL_TIMEOUT') || evt.content.includes('ERROR: DENIED'))) {
              setPendingApprovals(prev => prev.filter(p => p.tool !== evt.tool))
            }
          } else if (evt.type === 'tool_approval_required' && evt.approvalId) {
            const params =
              typeof evt.params === 'string' ? evt.params : JSON.stringify(evt.params ?? {})
            setPendingApprovals(prev => {
              if (prev.some(p => p.approvalId === evt.approvalId)) return prev
              return [
                ...prev,
                {
                  approvalId: evt.approvalId,
                  tool: evt.tool,
                  params,
                  sessionId: evt.sessionId,
                },
              ]
            })
            pushTool({
              role: 'tool',
              tool: evt.tool,
              toolKind: evt.toolKind ?? 'unknown',
              toolState: 'result',
              content: `等待批准 (${evt.approvalId.slice(0, 8)}…)`,
            })
          } else if (evt.type === 'tool_denied') {
            pushTool({
              role: 'tool',
              tool: evt.tool,
              toolKind: evt.toolKind ?? 'unknown',
              toolState: 'result',
              content: evt.content || '已拒绝',
            })
            setPendingApprovals(prev => prev.filter(p => p.tool !== evt.tool))
            if (typeof evt.content === 'string' && evt.content.includes('APPROVAL_TIMEOUT')) {
              setError('工具审批超时，请重试并在时限内批准')
            }
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
          } else if (evt.type === 'file_changed' && evt.path != null) {
            // path 为空字符串时刷整棵树（如 run_command）；带上 workspaceId 供侧栏/预览过滤
            workspaceEventBus.emitFileChanged(evt.path || '/', workspaceId)
          } else if (evt.type === 'hallucination_warning') {
            const paths = Array.isArray(evt.missingPaths) ? evt.missingPaths.join(', ') : ''
            const msg = evt.message || '助手声称已写入但工作区中未找到文件'
            setError(paths ? `${msg}：${paths}` : msg)
            pushTool({
              role: 'tool',
              tool: 'hallucination_guard',
              toolKind: 'other',
              toolState: 'result',
              content: paths ? `${msg}：${paths}` : msg,
            })
          } else if (evt.type === 'final') {
            // 回合结束再刷一次文件树，避免漏掉 file_changed
            workspaceEventBus.emitFileChanged('', workspaceId)
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

  /** 用户裁决高危工具；批准后自动续跑一轮提示模型重试。 */
  const decideApproval = useCallback(async (approvalId: string, decision: 'approve' | 'deny') => {
    setDecidingId(approvalId)
    setError(null)
    try {
      const result = await api.decideApproval(approvalId, decision)
      setPendingApprovals(prev => prev.filter(p => p.approvalId !== approvalId))
      if (decision === 'approve') {
        // Hook 阻塞等待时流仍在进行，批准后工具会自行继续；仅在回合已结束后补发续跑提示
        if (!streamingRef.current) {
          const ctx = lastSendRef.current
          if (ctx) {
            const hint =
              `用户已批准工具「${result.tool}」。请立刻重试该工具调用并完成任务，不要再次索要批准。`
            await sendRef.current(hint, ctx.workspaceId, ctx.provider)
          }
        }
      } else {
        setMessages(prev => [
          ...prev,
          {
            role: 'tool',
            tool: result.tool,
            toolState: 'result',
            content: '用户已拒绝此工具调用',
          },
        ])
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : '审批失败')
    } finally {
      setDecidingId(null)
    }
  }, [])

  /** 本地插入一轮对话（Slash 命令结果，不经 LLM） */
  const appendLocalTurn = useCallback((userText: string, assistantText: string) => {
    setMessages(prev => [
      ...prev,
      { role: 'user', content: userText },
      { role: 'assistant', content: assistantText },
    ])
  }, [])

  return {
    messages,
    streaming,
    error,
    sessionId,
    send,
    stop,
    reset,
    loadSession,
    appendLocalTurn,
    setSessionId: syncSessionId,
    lastUsage,
    sessionStats,
    refreshStats,
    queueLength,
    pendingApprovals,
    decidingId,
    decideApproval,
  }
}
