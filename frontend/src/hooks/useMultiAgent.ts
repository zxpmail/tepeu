/**
 * 多 Agent SSE 消费 — Planner/Implementer/Reviewer 流水线状态 + 工具审批。
 * 关联：MultiAgentView、ApprovalBanner、api.decideApproval。
 */
import { useCallback, useRef, useState, type Dispatch, type SetStateAction } from 'react'
import { api } from '../api/client'
import type { PendingApproval } from '../components/chat/ApprovalBanner'

export type RoleName = 'PLANNER' | 'IMPLEMENTER' | 'REVIEWER'

export interface RoleStep {
  role: RoleName
  status: 'running' | 'done' | 'failed'
  content?: string
  reason?: string
}

export interface MultiAgentState {
  runId?: string
  sessionId?: string
  steps: RoleStep[]
  finalStatus?: 'succeeded' | 'failed'
  error?: string | null
  running: boolean
  pendingApprovals: PendingApproval[]
  decidingId: string | null
}

type SseEvent = {
  type: string
  runId?: string
  sessionId?: string
  role?: RoleName
  content?: string
  reason?: string
  status?: 'succeeded' | 'failed'
  ok?: boolean
  message?: string
  code?: string
  acceptanceCriteria?: string
  goal?: string
  approvalId?: string
  tool?: string
  params?: string | Record<string, unknown>
}

const empty: MultiAgentState = {
  steps: [],
  running: false,
  error: null,
  pendingApprovals: [],
  decidingId: null,
}

export function useMultiAgent() {
  const [state, setState] = useState<MultiAgentState>(empty)
  const abortRef = useRef<AbortController | null>(null)

  const reset = useCallback(() => {
    abortRef.current?.abort()
    abortRef.current = null
    setState(empty)
  }, [])

  const stop = useCallback(() => {
    abortRef.current?.abort()
  }, [])

  const decideApproval = useCallback(async (approvalId: string, decision: 'approve' | 'deny') => {
    setState(prev => ({ ...prev, decidingId: approvalId, error: null }))
    try {
      await api.decideApproval(approvalId, decision)
      setState(prev => ({
        ...prev,
        pendingApprovals: prev.pendingApprovals.filter(p => p.approvalId !== approvalId),
        decidingId: null,
      }))
    } catch (e) {
      setState(prev => ({
        ...prev,
        decidingId: null,
        error: e instanceof Error ? e.message : '审批失败',
      }))
    }
  }, [])

  const run = useCallback(async (opts: {
    goal: string
    acceptanceCriteria?: string
    workspaceId: string
    provider: string
    sessionId?: string
  }) => {
    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller
    setState({ ...empty, running: true })

    try {
      const res = await fetch('/api/multi-agent/stream', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          goal: opts.goal,
          acceptanceCriteria: opts.acceptanceCriteria || undefined,
          workspaceId: opts.workspaceId,
          provider: opts.provider,
          sessionId: opts.sessionId,
        }),
        signal: controller.signal,
      })
      if (!res.ok || !res.body) {
        let msg = `HTTP ${res.status}`
        try {
          const b = await res.json()
          msg = b?.message || msg
        } catch { /* keep */ }
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
          try {
            evt = JSON.parse(json)
          } catch {
            continue
          }
          applyEvent(evt, setState)
        }
      }
    } catch (e) {
      if ((e as Error)?.name === 'AbortError') return
      setState(prev => ({
        ...prev,
        running: false,
        error: e instanceof Error ? e.message : 'Multi-agent failed',
      }))
      return
    } finally {
      abortRef.current = null
      setState(prev => ({ ...prev, running: false }))
    }
  }, [])

  return { ...state, run, reset, stop, decideApproval }
}

function applyEvent(
  evt: SseEvent,
  setState: Dispatch<SetStateAction<MultiAgentState>>,
) {
  if (evt.type === 'error') {
    setState(prev => ({
      ...prev,
      error: evt.message || evt.code || 'error',
      running: false,
    }))
    return
  }
  if (evt.type === 'session' && evt.sessionId) {
    setState(prev => ({ ...prev, sessionId: evt.sessionId }))
    return
  }
  if (evt.type === 'tool_approval_required' && evt.approvalId) {
    const params =
      typeof evt.params === 'string' ? evt.params : JSON.stringify(evt.params ?? {})
    setState(prev => {
      if (prev.pendingApprovals.some(p => p.approvalId === evt.approvalId)) return prev
      return {
        ...prev,
        pendingApprovals: [
          ...prev.pendingApprovals,
          {
            approvalId: evt.approvalId!,
            tool: evt.tool || 'tool',
            params,
            sessionId: evt.sessionId,
          },
        ],
      }
    })
    return
  }
  if (evt.type === 'tool_denied' && evt.tool) {
    const timeout = typeof evt.content === 'string' && evt.content.includes('APPROVAL_TIMEOUT')
    setState(prev => ({
      ...prev,
      error: timeout ? '工具审批超时，请重试并在时限内批准' : `工具已拒绝：${evt.tool}`,
      pendingApprovals: prev.pendingApprovals.filter(p => p.tool !== evt.tool),
    }))
    return
  }
  if (evt.type === 'tool_result' && evt.tool && typeof evt.content === 'string'
      && (evt.content.includes('APPROVAL_TIMEOUT') || evt.content.includes('ERROR: DENIED'))) {
    const content = evt.content
    setState(prev => ({
      ...prev,
      pendingApprovals: prev.pendingApprovals.filter(p => p.tool !== evt.tool),
      error: content.includes('APPROVAL_TIMEOUT')
        ? '工具审批超时，请重试并在时限内批准'
        : prev.error,
    }))
    return
  }
  if (evt.type === 'multi_agent_start') {
    setState(prev => ({ ...prev, runId: evt.runId, steps: [] }))
    return
  }
  if (evt.type === 'agent_role_start' && evt.role) {
    setState(prev => ({
      ...prev,
      steps: [...prev.steps.filter(s => s.role !== evt.role), { role: evt.role!, status: 'running' }],
    }))
    return
  }
  if (evt.type === 'agent_role_done' && evt.role) {
    setState(prev => ({
      ...prev,
      steps: prev.steps.map(s =>
        s.role === evt.role
          ? { ...s, status: 'done', content: evt.content }
          : s,
      ),
    }))
    return
  }
  if (evt.type === 'agent_role_failed' && evt.role) {
    setState(prev => ({
      ...prev,
      steps: prev.steps.map(s =>
        s.role === evt.role
          ? { ...s, status: 'failed', reason: evt.reason, content: evt.content }
          : s,
      ),
    }))
    return
  }
  if (evt.type === 'multi_agent_final') {
    setState(prev => ({
      ...prev,
      finalStatus: evt.status,
      running: false,
      pendingApprovals: [],
    }))
  }
}
