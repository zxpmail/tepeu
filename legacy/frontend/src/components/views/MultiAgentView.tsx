/**
 * 多 Agent 协作面板 — Goal + 验收标准 + 三角色时间线 + 工具审批。
 * 关联：useMultiAgent、ApprovalBanner、App 次级面板。
 */
import { useEffect, useState } from 'react'
import { api } from '../../api/client'
import ApprovalBanner from '../chat/ApprovalBanner'
import { useMultiAgent, type RoleName } from '../../hooks/useMultiAgent'
import type { ProviderMetadata } from '../../types'

interface MultiAgentViewProps {
  workspaceId?: string
  onOpenSession?: (sessionId: string) => void
}

const ROLE_LABEL: Record<RoleName, string> = {
  PLANNER: 'Planner 规划',
  IMPLEMENTER: 'Implementer 执行',
  REVIEWER: 'Reviewer 审查',
}

export default function MultiAgentView({ workspaceId, onOpenSession }: MultiAgentViewProps) {
  const {
    steps, running, error, finalStatus, sessionId, run, reset, stop,
    pendingApprovals, decidingId, decideApproval,
  } = useMultiAgent()
  const [goal, setGoal] = useState('')
  const [acceptance, setAcceptance] = useState('')
  const [providers, setProviders] = useState<ProviderMetadata[]>([])
  const [provider, setProvider] = useState('')

  useEffect(() => {
    api.getAvailableProviders()
      .then(list => {
        const enabled = list.filter(p => p.enabled)
        const pick = enabled.length > 0 ? enabled : list
        setProviders(pick)
        if (pick[0]) setProvider(pick[0].id)
      })
      .catch(() => {})
  }, [])

  const canRun = !!workspaceId && !!provider && goal.trim().length > 0 && !running

  const handleStart = () => {
    if (!workspaceId || !provider || !goal.trim()) return
    void run({
      goal: goal.trim(),
      acceptanceCriteria: acceptance.trim() || undefined,
      workspaceId,
      provider,
    })
  }

  if (!workspaceId) {
    return (
      <div className="p-6" style={{ color: 'var(--color-text-secondary)' }}>
        请先选择工作区。
      </div>
    )
  }

  return (
    <div className="p-6 max-w-2xl space-y-4">
      <div>
        <h1 className="text-lg font-semibold" style={{ color: 'var(--color-text)' }}>
          多 Agent 协作
        </h1>
        <p className="text-xs mt-1" style={{ color: 'var(--color-text-dim)' }}>
          Planner → Implementer → Reviewer；共享工作区与工具审批；失败步骤可见。
        </p>
      </div>

      <label className="block text-xs mb-1" style={{ color: 'var(--color-text-secondary)' }}>
        服务商
      </label>
      <select
        value={provider}
        onChange={e => setProvider(e.target.value)}
        className="w-full px-3 py-2 text-sm rounded border mb-3"
        style={{
          borderColor: 'var(--color-border)',
          backgroundColor: 'var(--color-bg)',
          color: 'var(--color-text)',
        }}
        disabled={running}
      >
        {providers.length === 0 && <option value="">无可用服务商</option>}
        {providers.map(p => (
          <option key={p.id} value={p.id}>{p.name}</option>
        ))}
      </select>

      <label className="block text-xs mb-1" style={{ color: 'var(--color-text-secondary)' }}>
        目标 Goal
      </label>
      <textarea
        data-testid="multi-goal-input"
        value={goal}
        onChange={e => setGoal(e.target.value)}
        rows={3}
        disabled={running}
        placeholder="例如：在工作区创建 hello.txt，内容为 Hello Tepeu"
        className="w-full px-3 py-2 text-sm rounded border mb-3"
        style={{
          borderColor: 'var(--color-border)',
          backgroundColor: 'var(--color-bg)',
          color: 'var(--color-text)',
        }}
      />

      <label className="block text-xs mb-1" style={{ color: 'var(--color-text-secondary)' }}>
        验收标准（可选）
      </label>
      <textarea
        value={acceptance}
        onChange={e => setAcceptance(e.target.value)}
        rows={2}
        disabled={running}
        placeholder="例如：hello.txt 存在且内容正确"
        className="w-full px-3 py-2 text-sm rounded border mb-3"
        style={{
          borderColor: 'var(--color-border)',
          backgroundColor: 'var(--color-bg)',
          color: 'var(--color-text)',
        }}
      />

      <div className="flex gap-2">
        <button
          type="button"
          data-testid="multi-start"
          disabled={!canRun}
          onClick={handleStart}
          className="px-3 py-1.5 text-sm rounded"
          style={{
            backgroundColor: canRun ? 'var(--color-accent)' : 'var(--color-bg-tertiary)',
            color: canRun ? '#fff' : 'var(--color-text-dim)',
          }}
        >
          {running ? '运行中…' : '开始协作'}
        </button>
        {running && (
          <button type="button" onClick={stop} className="px-3 py-1.5 text-sm rounded"
            style={{ color: 'var(--color-text-secondary)' }}>
            停止
          </button>
        )}
        <button type="button" onClick={reset} className="px-3 py-1.5 text-sm rounded"
          style={{ color: 'var(--color-text-secondary)' }}>
          清空
        </button>
      </div>

      {sessionId && (
        <div className="flex items-center gap-2 text-[11px]" style={{ color: 'var(--color-text-dim)' }}>
          <span>会话 {sessionId.slice(0, 8)}…</span>
          {onOpenSession && (
            <button
              type="button"
              data-testid="multi-open-session"
              className="underline"
              style={{ color: 'var(--color-accent)' }}
              onClick={() => onOpenSession(sessionId)}
            >
              打开对话
            </button>
          )}
        </div>
      )}

      <ApprovalBanner
        items={pendingApprovals}
        decidingId={decidingId}
        onDecide={(id, d) => void decideApproval(id, d)}
      />

      {error && (
        <div className="text-sm px-3 py-2 rounded border"
          style={{
            borderColor: 'color-mix(in srgb, var(--color-danger) 35%, var(--color-border))',
            color: 'var(--color-danger)',
          }}>
          {error}
        </div>
      )}

      {finalStatus && (
        <div className="text-sm font-medium" style={{
          color: finalStatus === 'succeeded' ? 'var(--color-accent)' : 'var(--color-danger)',
        }}>
          结果：{finalStatus === 'succeeded' ? '成功' : '失败'}
        </div>
      )}

      <div className="space-y-2">
        {steps.map(step => (
          <div
            key={step.role}
            className="rounded-lg border px-3 py-2"
            style={{
              borderColor: 'var(--color-border)',
              backgroundColor: 'var(--color-bg-secondary)',
            }}
          >
            <div className="flex items-center justify-between text-sm" style={{ color: 'var(--color-text)' }}>
              <span>{ROLE_LABEL[step.role]}</span>
              <span className="text-xs" style={{ color: 'var(--color-text-dim)' }}>
                {step.status === 'running' && '进行中'}
                {step.status === 'done' && '完成'}
                {step.status === 'failed' && '失败'}
              </span>
            </div>
            {step.reason && (
              <div className="text-xs mt-1" style={{ color: 'var(--color-danger)' }}>{step.reason}</div>
            )}
            {step.content && (
              <pre className="text-[11px] mt-2 max-h-40 overflow-auto whitespace-pre-wrap break-words"
                style={{ color: 'var(--color-text-dim)' }}>
                {step.content}
              </pre>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
