/**
 * 技能面板 — 列表启用；从 URL / ZIP 安装（可选手动粘贴）。
 * 关联：api.installSkillFromUrl / installSkillFromZip、App。
 */
import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '../../api/client'
import type { Skill } from '../../types'

interface SkillsViewProps {
  workspaceId: string | undefined
}

export default function SkillsView({ workspaceId }: SkillsViewProps) {
  const [skills, setSkills] = useState<Skill[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [url, setUrl] = useState('')
  const [zipFile, setZipFile] = useState<File | null>(null)
  const [paste, setPaste] = useState('')
  const [showPaste, setShowPaste] = useState(false)
  const [installing, setInstalling] = useState(false)
  const [busyId, setBusyId] = useState<string | null>(null)
  const [msg, setMsg] = useState<string | null>(null)

  const [installingPack, setInstallingPack] = useState(false)

  const reload = useCallback(async () => {
    if (!workspaceId) {
      setSkills([])
      return
    }
    setLoading(true)
    setError(null)
    try {
      setSkills(await api.listSkills(workspaceId))
    } catch (e) {
      setError(e instanceof Error ? e.message : '加载失败')
    } finally {
      setLoading(false)
    }
  }, [workspaceId])

  useEffect(() => { void reload() }, [reload])

  const toggle = async (s: Skill) => {
    setBusyId(s.id)
    setMsg(null)
    try {
      const updated = await api.setSkillEnabled(s.id, !s.enabled)
      setSkills(prev => prev.map(x => (x.id === updated.id ? updated : x)))
      setMsg(updated.enabled ? `已把「${updated.name}」标为常用（/ 菜单靠前）` : `已取消「${updated.name}」常用标记`)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '更新失败')
    } finally {
      setBusyId(null)
    }
  }

  const installPack = async () => {
    if (!workspaceId) return
    setInstallingPack(true)
    setError(null)
    setMsg(null)
    try {
      const r = await api.installReqForgeCodingPack(workspaceId)
      if (r.failed > 0) {
        setMsg(`已安装 ${r.installed} 项，失败 ${r.failed}：${(r.errors || []).slice(0, 2).join('；')}`)
      } else {
        setMsg(`已安装 ReqForge 编程套件（${r.installed} 项）。在对话里输入 /dev-builder 等调用。`)
      }
      await reload()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '套件安装失败（需能访问 GitHub）')
    } finally {
      setInstallingPack(false)
    }
  }

  const install = async () => {
    if (!workspaceId) return
    setInstalling(true)
    setError(null)
    setMsg(null)
    try {
      let s: Skill
      if (zipFile) {
        s = await api.installSkillFromZip(workspaceId, zipFile, name.trim() || undefined)
      } else if (url.trim()) {
        s = await api.installSkillFromUrl(workspaceId, url.trim(), name.trim() || undefined)
      } else if (paste.trim()) {
        s = await api.installSkill(workspaceId, paste.trim(), name.trim() || undefined)
      } else {
        setError('请填写网址，或选择 ZIP 包')
        return
      }
      setUrl('')
      setZipFile(null)
      setPaste('')
      setName('')
      setMsg(`已安装「${s.name}」，对话中输入 /${s.slug} 或 @${s.slug} 调用`)
      await reload()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '安装失败')
    } finally {
      setInstalling(false)
    }
  }

  const remove = async (s: Skill) => {
    if (s.builtin) return
    if (!window.confirm(`卸载技能「${s.name}」？`)) return
    setBusyId(s.id)
    try {
      await api.deleteSkill(s.id)
      setSkills(prev => prev.filter(x => x.id !== s.id))
      setMsg(`已卸载「${s.name}」`)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '卸载失败')
    } finally {
      setBusyId(null)
    }
  }

  const canInstall = Boolean(zipFile || url.trim() || paste.trim())

  if (!workspaceId) {
    return (
      <div className="p-6 text-sm" style={{ color: 'var(--color-text-dim)' }}>
        请先选择工作区
      </div>
    )
  }

  return (
    <div className="max-w-3xl mx-auto p-6 space-y-6">
      <div>
        <h1 className="text-lg font-semibold" style={{ color: 'var(--color-text)' }}>技能</h1>
        <p className="text-xs mt-1" style={{ color: 'var(--color-text-dim)' }}>
          安装后在对话里用 /技能名 或 @技能名 调用（例如 /dev-builder 写个接口）。「常用」只影响 / 菜单排序。
        </p>
      </div>

      <section
        className="p-4 rounded border space-y-2"
        style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg-secondary)' }}
      >
        <div className="text-sm font-medium" style={{ color: 'var(--color-text)' }}>
          ReqForge 编程套件
        </div>
        <p className="text-xs" style={{ color: 'var(--color-text-dim)' }}>
          从 github.com/zxpmail/ReqForge 拉取开发所需 skill 与 agent；若 GitHub 不可达，会自动使用本机 E:\work\ReqForge。
          安装后用 /dev-builder、/bug-fixer 等调用。
        </p>
        <button
          type="button"
          onClick={() => void installPack()}
          disabled={installingPack}
          className="px-4 py-2 text-sm rounded"
          style={{
            backgroundColor: 'var(--color-accent)',
            color: '#fff',
            opacity: installingPack ? 0.6 : 1,
          }}
        >
          {installingPack ? '正在从 GitHub 安装…' : '一键安装'}
        </button>
      </section>

      {msg && (
        <div className="text-xs px-3 py-2 rounded" style={{ backgroundColor: 'var(--color-bg-secondary)', color: 'var(--color-text)' }}>
          {msg}
        </div>
      )}
      {error && (
        <div className="text-xs px-3 py-2 rounded" style={{ color: 'var(--color-danger)' }}>{error}</div>
      )}

      <section className="space-y-2">
        <div className="text-xs font-medium uppercase tracking-wide" style={{ color: 'var(--color-text-dim)' }}>
          已安装 {loading ? '…' : `(${skills.length})`}
        </div>
        {skills.length === 0 && !loading && (
          <div className="text-sm" style={{ color: 'var(--color-text-dim)' }}>暂无技能</div>
        )}
        {skills.map(s => (
          <div
            key={s.id}
            className="flex items-start gap-3 p-3 rounded border"
            style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg-secondary)' }}
          >
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2">
                <span className="text-sm font-medium truncate" style={{ color: 'var(--color-text)' }}>
                  {s.name}
                </span>
                {s.builtin && (
                  <span className="text-[10px] px-1.5 py-0.5 rounded" style={{ color: 'var(--color-text-dim)', backgroundColor: 'var(--color-bg)' }}>
                    内置
                  </span>
                )}
              </div>
              {s.description && (
                <div className="text-xs mt-0.5 truncate" style={{ color: 'var(--color-text-dim)' }}>
                  {s.description}
                </div>
              )}
            </div>
            <label className="flex items-center gap-1.5 text-xs shrink-0 cursor-pointer" style={{ color: 'var(--color-text-secondary)' }}>
              <input
                type="checkbox"
                checked={s.enabled}
                disabled={busyId === s.id}
                onChange={() => void toggle(s)}
              />
              常用
            </label>
            {!s.builtin && (
              <button
                type="button"
                className="text-xs shrink-0"
                style={{ color: 'var(--color-danger)' }}
                disabled={busyId === s.id}
                onClick={() => void remove(s)}
              >
                卸载
              </button>
            )}
          </div>
        ))}
      </section>

      <section className="space-y-2">
        <div className="text-xs font-medium uppercase tracking-wide" style={{ color: 'var(--color-text-dim)' }}>
          安装技能
        </div>
        <input
          value={name}
          onChange={e => setName(e.target.value)}
          placeholder="名称（可选）"
          className="w-full px-3 py-2 text-sm rounded border outline-none"
          style={{
            borderColor: 'var(--color-border)',
            backgroundColor: 'var(--color-bg)',
            color: 'var(--color-text)',
          }}
        />
        <input
          value={url}
          onChange={e => { setUrl(e.target.value); if (e.target.value) setZipFile(null) }}
          placeholder="技能网址（https://…/SKILL.md 或 …/skill.zip）"
          className="w-full px-3 py-2 text-sm rounded border outline-none"
          style={{
            borderColor: 'var(--color-border)',
            backgroundColor: 'var(--color-bg)',
            color: 'var(--color-text)',
          }}
        />
        <div className="flex items-center gap-2">
          <label
            className="text-xs px-3 py-2 rounded border cursor-pointer"
            style={{ borderColor: 'var(--color-border)', color: 'var(--color-text-secondary)' }}
          >
            选择 ZIP 包
            <input
              type="file"
              accept=".zip,application/zip"
              className="hidden"
              onChange={e => {
                const f = e.target.files?.[0] ?? null
                setZipFile(f)
                if (f) setUrl('')
              }}
            />
          </label>
          <span className="text-xs truncate" style={{ color: 'var(--color-text-dim)' }}>
            {zipFile ? zipFile.name : '未选择文件'}
          </span>
        </div>
        <button
          type="button"
          className="text-xs underline"
          style={{ color: 'var(--color-text-dim)' }}
          onClick={() => setShowPaste(v => !v)}
        >
          {showPaste ? '收起粘贴安装' : '改用粘贴 Markdown'}
        </button>
        {showPaste && (
          <textarea
            value={paste}
            onChange={e => setPaste(e.target.value)}
            placeholder="粘贴 SKILL.md 全文"
            rows={8}
            className="w-full px-3 py-2 text-xs rounded border outline-none font-mono"
            style={{
              borderColor: 'var(--color-border)',
              backgroundColor: 'var(--color-bg)',
              color: 'var(--color-text)',
            }}
          />
        )}
        <button
          type="button"
          onClick={() => void install()}
          disabled={installing || !canInstall}
          className="px-4 py-2 text-sm rounded"
          style={{
            backgroundColor: 'var(--color-accent)',
            color: '#fff',
            opacity: installing || !canInstall ? 0.6 : 1,
          }}
        >
          {installing ? '安装中…' : '安装'}
        </button>
      </section>
    </div>
  )
}
