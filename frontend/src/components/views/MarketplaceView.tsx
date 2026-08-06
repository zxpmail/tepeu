/**
 * 应用市场 — 浏览/搜索技能目录，一键安装到当前工作区。
 * 关联：api.getMarketplaceCatalog / installMarketplaceSkill、App。
 */
import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '../../api/client'
import type { MarketplaceCatalog, MarketplaceEntry } from '../../types'

interface MarketplaceViewProps {
  workspaceId: string | undefined
}

function availabilityLabel(a: string): string {
  switch (a) {
    case 'builtin': return '内置'
    case 'local': return '本机'
    case 'github': return 'GitHub'
    case 'remote': return '远程'
    default: return '不可用'
  }
}

export default function MarketplaceView({ workspaceId }: MarketplaceViewProps) {
  const [catalog, setCatalog] = useState<MarketplaceCatalog | null>(null)
  const [q, setQ] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [msg, setMsg] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<string | null>(null)

  const reload = useCallback(async (query?: string) => {
    setLoading(true)
    setError(null)
    try {
      setCatalog(await api.getMarketplaceCatalog(workspaceId, query ?? q))
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载目录失败')
    } finally {
      setLoading(false)
    }
  }, [workspaceId, q])

  useEffect(() => {
    void reload('')
  }, [workspaceId]) // eslint-disable-line react-hooks/exhaustive-deps -- 仅切工作区时重载

  const search = () => { void reload(q) }

  const install = async (entry: MarketplaceEntry) => {
    if (!workspaceId) return
    if (entry.availability === 'unavailable') {
      setError('该条目当前不可安装')
      return
    }
    setBusyId(entry.id)
    setError(null)
    setMsg(null)
    try {
      const skill = await api.installMarketplaceSkill(workspaceId, entry.id)
      setMsg(`已安装「${skill.name}」。在对话里输入 /${skill.slug} 调用。`)
      await reload(q)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '安装失败')
    } finally {
      setBusyId(null)
    }
  }

  if (!workspaceId) {
    return (
      <div className="p-6" style={{ color: 'var(--color-text-secondary)' }}>
        请先选择工作区，再从市场安装技能。
      </div>
    )
  }

  const entries = catalog?.entries ?? []

  return (
    <div className="p-6 max-w-2xl space-y-4">
      <div>
        <h1 className="text-lg font-semibold" style={{ color: 'var(--color-text)' }}>应用市场</h1>
        <p className="text-xs mt-1" style={{ color: 'var(--color-text-dim)' }}>
          浏览技能目录并一键安装到当前工作区。不配远程清单时，仍可用内置与本机 ReqForge 目录。
        </p>
      </div>

      <div className="flex gap-2">
        <input
          type="search"
          value={q}
          onChange={(e) => setQ(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') search() }}
          placeholder="搜索名称、描述…"
          className="flex-1 text-sm px-3 py-2 rounded border outline-none"
          style={{
            borderColor: 'var(--color-border)',
            backgroundColor: 'var(--color-bg)',
            color: 'var(--color-text)',
          }}
          data-testid="marketplace-search"
        />
        <button
          type="button"
          onClick={search}
          className="text-sm px-3 py-2 rounded"
          style={{ backgroundColor: 'var(--color-accent)', color: '#fff' }}
        >
          搜索
        </button>
      </div>

      {catalog && (
        <p className="text-xs" style={{ color: 'var(--color-text-dim)' }}>
          目录 v{catalog.catalogVersion}
          {catalog.localRoot ? ` · 本机 ${catalog.localRoot}` : ' · 未检测到本机 ReqForge'}
          {catalog.remoteManifestUrl
            ? (catalog.remoteLoaded ? ' · 远程清单已加载' : ` · 远程失败：${catalog.remoteError || '未知'}`)
            : ' · 未配置远程清单'}
        </p>
      )}

      {error && (
        <div
          className="text-sm px-3 py-2 rounded border"
          style={{
            borderColor: 'color-mix(in srgb, var(--color-danger) 35%, var(--color-border))',
            color: 'var(--color-danger)',
          }}
          data-testid="marketplace-error"
        >
          {error}
        </div>
      )}
      {msg && (
        <div
          className="text-sm px-3 py-2 rounded border"
          style={{
            borderColor: 'color-mix(in srgb, var(--color-accent) 35%, var(--color-border))',
            color: 'var(--color-text)',
          }}
          data-testid="marketplace-msg"
        >
          {msg}
        </div>
      )}

      {loading && (
        <p className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>加载中…</p>
      )}

      {!loading && entries.length === 0 && (
        <p className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>没有匹配的技能。</p>
      )}

      <ul className="space-y-3" data-testid="marketplace-list">
        {entries.map((e) => (
          <li
            key={e.id}
            className="rounded border px-3 py-3"
            style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg-secondary)' }}
          >
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="font-medium text-sm" style={{ color: 'var(--color-text)' }}>
                    {e.name}
                  </span>
                  <span className="text-xs" style={{ color: 'var(--color-text-dim)' }}>
                    /{e.slug}
                  </span>
                  {e.version && (
                    <span className="text-xs" style={{ color: 'var(--color-text-dim)' }}>
                      v{e.version}
                    </span>
                  )}
                  <span
                    className="text-xs px-1.5 py-0.5 rounded"
                    style={{
                      backgroundColor: 'color-mix(in srgb, var(--color-accent) 12%, transparent)',
                      color: 'var(--color-text-secondary)',
                    }}
                  >
                    {availabilityLabel(e.availability)}
                  </span>
                  {e.installed && (
                    <span className="text-xs" style={{ color: 'var(--color-accent)' }}>
                      已安装{e.installedVersion ? ` (${e.installedVersion})` : ''}
                    </span>
                  )}
                </div>
                {e.description && (
                  <p className="text-xs mt-1 leading-relaxed" style={{ color: 'var(--color-text-secondary)' }}>
                    {e.description}
                  </p>
                )}
              </div>
              <button
                type="button"
                disabled={busyId === e.id || e.availability === 'unavailable'}
                onClick={() => { void install(e) }}
                className="shrink-0 text-xs px-3 py-1.5 rounded disabled:opacity-50"
                style={{
                  backgroundColor: e.installed ? 'transparent' : 'var(--color-accent)',
                  color: e.installed ? 'var(--color-accent)' : '#fff',
                  border: e.installed ? '1px solid var(--color-accent)' : 'none',
                }}
                data-testid={`marketplace-install-${e.slug}`}
              >
                {busyId === e.id ? '安装中…' : e.installed ? '重新安装' : '安装'}
              </button>
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}
