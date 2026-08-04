/**
 * 自主 Agent 定时任务面板 — 列表/新建/编辑/启停/立即运行/打开会话。
 * 关联：api schedules、App 次级面板「自主」、sessionNavBus。
 */
import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '../../api/client'
import { onTaskEvent } from '../../hooks/useNotifications'
import type { AgentSchedule, ProviderMetadata } from '../../types'

interface ScheduleViewProps {
  workspaceId?: string
  onOpenSession?: (sessionId: string) => void
}

const STATUS_LABEL: Record<string, string> = {
  IDLE: '待命',
  RUNNING: '运行中',
  SUCCESS: '成功',
  FAILED: '失败',
  EMPTY: '空回复',
}

function formatTime(iso?: string | null): string {
  if (!iso) return '—'
  try {
    return new Date(iso).toLocaleString()
  } catch {
    return iso
  }
}

export default function ScheduleView({ workspaceId, onOpenSession }: ScheduleViewProps) {
  const [items, setItems] = useState<AgentSchedule[]>([])
  const [providers, setProviders] = useState<ProviderMetadata[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [prompt, setPrompt] = useState('')
  const [providerId, setProviderId] = useState('')
  const [intervalMinutes, setIntervalMinutes] = useState(60)
  const [saving, setSaving] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)

  const reload = useCallback(async () => {
    if (!workspaceId) return
    setLoading(true)
    setError(null)
    try {
      const list = await api.listSchedules(workspaceId)
      setItems(list)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [workspaceId])

  useEffect(() => {
    api.getAvailableProviders()
      .then(list => {
        const enabled = list.filter(p => p.enabled)
        const pick = enabled.length > 0 ? enabled : list
        setProviders(pick)
        setProviderId(prev => prev || pick[0]?.id || '')
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  useEffect(() => {
    if (!workspaceId) return
    const hasRunning = items.some(i => i.lastStatus === 'RUNNING')
    if (!hasRunning) return
    const t = window.setInterval(() => { void reload() }, 4000)
    return () => window.clearInterval(t)
  }, [items, workspaceId, reload])

  // 后台任务完成/失败事件（Phase 13）：仅刷新当前工作区
  useEffect(() => {
    return onTaskEvent((n) => {
      if (workspaceId && n.workspaceId === workspaceId) void reload()
    })
  }, [workspaceId, reload])

  const resetForm = () => {
    setEditingId(null)
    setName('')
    setPrompt('')
    setIntervalMinutes(60)
    if (providers[0]) setProviderId(providers[0].id)
  }

  const startEdit = (s: AgentSchedule) => {
    setEditingId(s.id)
    setName(s.name)
    setPrompt(s.prompt)
    setProviderId(s.providerId)
    setIntervalMinutes(s.intervalMinutes)
  }

  const handleSave = async () => {
    if (!workspaceId || !name.trim() || !prompt.trim() || !providerId) return
    setSaving(true)
    setError(null)
    try {
      if (editingId) {
        await api.updateSchedule(editingId, {
          name: name.trim(),
          prompt: prompt.trim(),
          providerId,
          intervalMinutes,
        })
      } else {
        await api.createSchedule({
          workspaceId,
          name: name.trim(),
          prompt: prompt.trim(),
          providerId,
          intervalMinutes,
          enabled: true,
        })
      }
      resetForm()
      await reload()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : (editingId ? '保存失败' : '创建失败'))
    } finally {
      setSaving(false)
    }
  }

  const toggleEnabled = async (s: AgentSchedule) => {
    try {
      await api.updateSchedule(s.id, { enabled: !s.enabled })
      await reload()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '更新失败')
    }
  }

  const runNow = async (id: string) => {
    try {
      await api.runSchedule(id)
      await reload()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '触发失败')
    }
  }

  const remove = async (id: string) => {
    if (!window.confirm('确定删除此自主任务？')) return
    try {
      await api.deleteSchedule(id)
      if (editingId === id) resetForm()
      await reload()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '删除失败')
    }
  }

  if (!workspaceId) {
    return (
      <div className="p-6" style={{ color: 'var(--color-text-secondary)' }}>
        请先选择工作区。
      </div>
    )
  }

  return (
    <div className="p-6 max-w-2xl space-y-5" data-testid="schedule-view">
      <div>
        <h1 className="text-lg font-semibold" style={{ color: 'var(--color-text)' }}>
          自主 Agent
        </h1>
        <p className="text-xs mt-1" style={{ color: 'var(--color-text-dim)' }}>
          按间隔自动开会话执行提示词；可启停、编辑与立即运行。失败/空回复会显示在列表中。
        </p>
      </div>

      {error && (
        <div className="text-sm px-3 py-2 rounded" style={{ color: 'var(--color-danger, #c44)', backgroundColor: 'var(--color-bg-secondary)' }}>
          {error}
        </div>
      )}

      <section className="space-y-2 border rounded p-4" style={{ borderColor: 'var(--color-border)' }}>
        <h2 className="text-sm font-medium" style={{ color: 'var(--color-text)' }}>
          {editingId ? '编辑任务' : '新建任务'}
        </h2>
        <label className="block text-xs" style={{ color: 'var(--color-text-secondary)' }}>名称</label>
        <input
          data-testid="schedule-name-input"
          value={name}
          onChange={e => setName(e.target.value)}
          className="w-full px-3 py-2 text-sm rounded border"
          style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg)', color: 'var(--color-text)' }}
          placeholder="例如：每小时摘要"
        />
        <label className="block text-xs" style={{ color: 'var(--color-text-secondary)' }}>服务商</label>
        <select
          value={providerId}
          onChange={e => setProviderId(e.target.value)}
          className="w-full px-3 py-2 text-sm rounded border"
          style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg)', color: 'var(--color-text)' }}
        >
          {providers.length === 0 && <option value="">无可用服务商</option>}
          {providers.map(p => (
            <option key={p.id} value={p.id}>{p.name}</option>
          ))}
        </select>
        <label className="block text-xs" style={{ color: 'var(--color-text-secondary)' }}>间隔（分钟）</label>
        <input
          type="number"
          min={1}
          max={10080}
          value={intervalMinutes}
          onChange={e => setIntervalMinutes(Number(e.target.value) || 60)}
          className="w-full px-3 py-2 text-sm rounded border"
          style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg)', color: 'var(--color-text)' }}
        />
        <label className="block text-xs" style={{ color: 'var(--color-text-secondary)' }}>提示词 / 目标</label>
        <textarea
          data-testid="schedule-prompt-input"
          value={prompt}
          onChange={e => setPrompt(e.target.value)}
          rows={3}
          className="w-full px-3 py-2 text-sm rounded border"
          style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg)', color: 'var(--color-text)' }}
          placeholder="到期时会作为用户消息发给 Agent"
        />
        <div className="flex gap-2">
          <button
            type="button"
            data-testid="schedule-create-btn"
            disabled={saving || !name.trim() || !prompt.trim() || !providerId}
            onClick={() => { void handleSave() }}
            className="text-sm px-3 py-1.5 rounded"
            style={{
              backgroundColor: 'var(--color-accent)',
              color: '#fff',
              opacity: saving || !name.trim() || !prompt.trim() ? 0.5 : 1,
            }}
          >
            {saving ? '保存中…' : (editingId ? '保存修改' : '创建并启用')}
          </button>
          {editingId && (
            <button
              type="button"
              className="text-sm px-3 py-1.5 rounded border"
              style={{ borderColor: 'var(--color-border)', color: 'var(--color-text-secondary)' }}
              onClick={resetForm}
            >
              取消编辑
            </button>
          )}
        </div>
      </section>

      <section>
        <div className="flex items-center justify-between mb-2">
          <h2 className="text-sm font-medium" style={{ color: 'var(--color-text)' }}>任务列表</h2>
          <button type="button" className="text-xs" style={{ color: 'var(--color-accent)' }} onClick={() => { void reload() }}>
            刷新
          </button>
        </div>
        {loading && items.length === 0 ? (
          <div className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>加载中…</div>
        ) : items.length === 0 ? (
          <div className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>暂无自主任务。</div>
        ) : (
          <ul className="space-y-3">
            {items.map(s => (
              <li
                key={s.id}
                data-testid={`schedule-item-${s.id}`}
                className="border rounded p-3"
                style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg-secondary)' }}
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <div className="text-sm font-medium" style={{ color: 'var(--color-text)' }}>{s.name}</div>
                    <div className="text-xs mt-0.5" style={{ color: 'var(--color-text-dim)' }}>
                      每 {s.intervalMinutes} 分钟 · {s.providerId} · {s.enabled ? '已启用' : '已停用'}
                    </div>
                  </div>
                  <span
                    className="text-xs shrink-0"
                    style={{
                      color: (s.lastStatus === 'FAILED' || s.lastStatus === 'EMPTY')
                        ? 'var(--color-danger, #c44)'
                        : s.lastStatus === 'SUCCESS'
                          ? 'var(--color-accent)'
                          : 'var(--color-text-secondary)',
                    }}
                  >
                    {STATUS_LABEL[s.lastStatus ?? ''] ?? s.lastStatus ?? '—'}
                  </span>
                </div>
                <p className="text-xs mt-2 line-clamp-2" style={{ color: 'var(--color-text-secondary)' }}>{s.prompt}</p>
                <div className="text-xs mt-2 space-y-0.5" style={{ color: 'var(--color-text-dim)' }}>
                  <div>下次：{formatTime(s.nextRunAt)}</div>
                  <div>上次：{formatTime(s.lastRunAt)}</div>
                  {(s.lastStatus === 'SUCCESS' || s.lastStatus === 'FAILED' || s.lastStatus === 'EMPTY') && (
                    <div>
                      {s.lastStatus === 'SUCCESS' ? '完成' : '失败'}：{formatTime(s.updatedAt)}
                    </div>
                  )}
                  {s.lastSessionId && (
                    <div>
                      会话：
                      {onOpenSession ? (
                        <button
                          type="button"
                          className="underline ml-1"
                          style={{ color: 'var(--color-accent)' }}
                          onClick={() => onOpenSession(s.lastSessionId!)}
                        >
                          {s.lastSessionId.slice(0, 8)}… 打开
                        </button>
                      ) : (
                        <span> {s.lastSessionId.slice(0, 8)}…</span>
                      )}
                    </div>
                  )}
                  {s.lastError && (
                    <div style={{ color: 'var(--color-danger, #c44)' }}>错误：{s.lastError}</div>
                  )}
                </div>
                <div className="flex flex-wrap gap-2 mt-3">
                  <button
                    type="button"
                    className="text-xs px-2 py-1 rounded border"
                    style={{ borderColor: 'var(--color-border)', color: 'var(--color-text)' }}
                    onClick={() => { void toggleEnabled(s) }}
                  >
                    {s.enabled ? '停用' : '启用'}
                  </button>
                  <button
                    type="button"
                    className="text-xs px-2 py-1 rounded border"
                    style={{ borderColor: 'var(--color-border)', color: 'var(--color-text)' }}
                    onClick={() => startEdit(s)}
                  >
                    编辑
                  </button>
                  <button
                    type="button"
                    data-testid={`schedule-run-${s.id}`}
                    className="text-xs px-2 py-1 rounded border"
                    style={{ borderColor: 'var(--color-border)', color: 'var(--color-accent)' }}
                    disabled={s.lastStatus === 'RUNNING'}
                    onClick={() => { void runNow(s.id) }}
                  >
                    立即运行
                  </button>
                  <button
                    type="button"
                    className="text-xs px-2 py-1 rounded border ml-auto"
                    style={{ borderColor: 'var(--color-border)', color: 'var(--color-danger, #c44)' }}
                    onClick={() => { void remove(s.id) }}
                  >
                    删除
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  )
}
