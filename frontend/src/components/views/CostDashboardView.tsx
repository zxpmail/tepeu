/**
 * 成本仪表盘 — 工作区累计用量 + 预算条 + 告警/硬门禁设置。
 * 关联：api.getWorkspaceCost / updateWorkspaceBudget、App 次级面板。
 */
import { useCallback, useEffect, useState } from 'react'
import { api } from '../../api/client'
import type { BudgetStatus } from '../../types'

interface CostDashboardViewProps {
  workspaceId?: string
}

export default function CostDashboardView({ workspaceId }: CostDashboardViewProps) {
  const [status, setStatus] = useState<BudgetStatus | null>(null)
  const [budgetInput, setBudgetInput] = useState('')
  const [hardLimit, setHardLimit] = useState(false)
  const [threshold, setThreshold] = useState('0.8')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const refresh = useCallback(async () => {
    if (!workspaceId) {
      setStatus(null)
      return
    }
    setError(null)
    try {
      const s = await api.getWorkspaceCost(workspaceId)
      setStatus(s)
      setBudgetInput(s.budgetUsd != null ? String(s.budgetUsd) : '')
      setHardLimit(s.hardLimit)
      setThreshold(String(s.alertThreshold ?? 0.8))
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败')
    }
  }, [workspaceId])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const handleSave = async () => {
    if (!workspaceId) return
    setSaving(true)
    setError(null)
    try {
      const trimmed = budgetInput.trim()
      const budgetUsd = trimmed === '' ? null : Number(trimmed)
      if (budgetUsd != null && (Number.isNaN(budgetUsd) || budgetUsd < 0)) {
        throw new Error('预算须为非负数字，或留空表示不限制')
      }
      const alertThreshold = Number(threshold)
      if (Number.isNaN(alertThreshold) || alertThreshold <= 0 || alertThreshold > 1) {
        throw new Error('告警阈值须在 (0, 1]')
      }
      const s = await api.updateWorkspaceBudget(workspaceId, {
        budgetUsd,
        hardLimit,
        alertThreshold,
      })
      setStatus(s)
    } catch (e) {
      setError(e instanceof Error ? e.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }

  if (!workspaceId) {
    return (
      <div className="p-6" style={{ color: 'var(--color-text-secondary)' }}>
        请先选择工作区。
      </div>
    )
  }

  const ratioPct = status ? Math.min(100, Math.round((status.usageRatio || 0) * 100)) : 0
  const barColor = status?.blocked
    ? 'var(--color-danger)'
    : status?.alert
      ? 'color-mix(in srgb, var(--color-danger) 70%, var(--color-accent))'
      : 'var(--color-accent)'

  return (
    <div className="p-6 max-w-xl space-y-4">
      <div>
        <h1 className="text-lg font-semibold" style={{ color: 'var(--color-text)' }}>成本仪表盘</h1>
        <p className="text-xs mt-1" style={{ color: 'var(--color-text-dim)' }}>
          工作区累计用量、预算告警与硬门禁（超预算可阻断新对话）。
        </p>
      </div>

      {error && (
        <div className="text-sm px-3 py-2 rounded border"
          style={{ borderColor: 'color-mix(in srgb, var(--color-danger) 35%, var(--color-border))', color: 'var(--color-danger)' }}>
          {error}
        </div>
      )}

      {status && (
        <div className="rounded-lg border p-4 space-y-3"
          style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg-secondary)' }}>
          <div className="grid grid-cols-3 gap-2 text-center text-sm">
            <div>
              <div className="text-[11px]" style={{ color: 'var(--color-text-dim)' }}>Tokens</div>
              <div style={{ color: 'var(--color-text)' }}>{status.totalTokens}</div>
            </div>
            <div>
              <div className="text-[11px]" style={{ color: 'var(--color-text-dim)' }}>已用 USD</div>
              <div style={{ color: 'var(--color-text)' }}>${status.totalCostUsd.toFixed(4)}</div>
            </div>
            <div>
              <div className="text-[11px]" style={{ color: 'var(--color-text-dim)' }}>回合</div>
              <div style={{ color: 'var(--color-text)' }}>{status.turnCount}</div>
            </div>
          </div>

          {status.budgetUsd != null ? (
            <>
              <div className="h-2 rounded overflow-hidden" style={{ backgroundColor: 'var(--color-bg-tertiary)' }}>
                <div
                  className="h-full transition-all"
                  data-testid="cost-budget-bar"
                  style={{
                    width: `${status.budgetUsd === 0 ? (status.blocked || status.alert ? 100 : 0) : ratioPct}%`,
                    backgroundColor: barColor,
                  }}
                />
              </div>
              <div className="text-xs flex justify-between" style={{ color: 'var(--color-text-dim)' }}>
                <span>
                  {status.budgetUsd === 0 ? '预算 $0.00（零预算）' : `预算 $${status.budgetUsd.toFixed(2)}`}
                </span>
                <span>{status.budgetUsd === 0 ? (status.blocked ? '已阻断' : '告警') : `${ratioPct}%`}</span>
              </div>
            </>
          ) : (
            <div className="text-xs" style={{ color: 'var(--color-text-dim)' }}>未设置预算上限（不限制）</div>
          )}

          {status.alert && !status.blocked && (
            <div data-testid="cost-alert-banner" className="text-xs px-2 py-1 rounded" style={{ color: 'var(--color-danger)' }}>
              {status.budgetUsd === 0
                ? '零预算：已触发告警（未开硬门禁时仍可对话）'
                : `已超过告警阈值（${Math.round(status.alertThreshold * 100)}%）`}
            </div>
          )}
          {status.blocked && (
            <div
              data-testid="cost-blocked-banner"
              className="text-xs px-2 py-1 rounded border"
              style={{
                borderColor: 'color-mix(in srgb, var(--color-danger) 35%, var(--color-border))',
                color: 'var(--color-danger)',
              }}>
              硬门禁已生效：新对话与多 Agent 运行已被阻断
            </div>
          )}
        </div>
      )}

      <div className="space-y-3">
        <div>
          <label className="block text-xs mb-1" style={{ color: 'var(--color-text-secondary)' }}>
            预算上限（USD，留空=不限制，0=零预算）
          </label>
          <input
            data-testid="cost-budget-input"
            value={budgetInput}
            onChange={e => setBudgetInput(e.target.value)}
            className="w-full px-3 py-2 text-sm rounded border"
            style={{
              borderColor: 'var(--color-border)',
              backgroundColor: 'var(--color-bg)',
              color: 'var(--color-text)',
            }}
            placeholder="例如 10；0=零预算"
          />
        </div>
        <div>
          <label className="block text-xs mb-1" style={{ color: 'var(--color-text-secondary)' }}>
            告警阈值（0–1）
          </label>
          <input
            data-testid="cost-threshold-input"
            value={threshold}
            onChange={e => setThreshold(e.target.value)}
            className="w-full px-3 py-2 text-sm rounded border"
            style={{
              borderColor: 'var(--color-border)',
              backgroundColor: 'var(--color-bg)',
              color: 'var(--color-text)',
            }}
          />
        </div>
        <label className="flex items-center gap-2 text-sm" style={{ color: 'var(--color-text)' }}>
          <input
            data-testid="cost-hard-limit"
            type="checkbox"
            checked={hardLimit}
            onChange={e => setHardLimit(e.target.checked)}
          />
          启用硬门禁（超预算阻断新对话）
        </label>
        <div className="flex gap-2">
          <button
            type="button"
            data-testid="cost-save"
            disabled={saving}
            onClick={() => void handleSave()}
            className="px-3 py-1.5 text-sm rounded"
            style={{ backgroundColor: 'var(--color-accent)', color: '#fff' }}
          >
            {saving ? '保存中…' : '保存预算'}
          </button>
          <button
            type="button"
            onClick={() => void refresh()}
            className="px-3 py-1.5 text-sm rounded"
            style={{ color: 'var(--color-text-secondary)' }}
          >
            刷新
          </button>
        </div>
      </div>
    </div>
  )
}
