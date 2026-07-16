import { useState, useEffect, useRef } from 'react'
import { api, ApiError } from '../../api/client'
import type { ProviderMetadata, LlmProvider } from '../../types'

/**
 * LLM Provider configuration (§7.4).
 * API Key 用明文输入（可点「隐藏」），避免浏览器把 Base URL 自动填进 password 框导致存错值。
 */
export default function ProviderSettingsView() {
  const [providers, setProviders] = useState<ProviderMetadata[]>([])
  const [selected, setSelected] = useState('')
  const [apiKey, setApiKey] = useState('')
  const [baseUrl, setBaseUrl] = useState('')
  const [model, setModel] = useState('')
  const [enabled, setEnabled] = useState(false)
  const [maskedKey, setMaskedKey] = useState<string | null>(null)
  const [hideKey, setHideKey] = useState(false)
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState(false)
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null)
  const apiKeyRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    api.getAvailableProviders()
      .then(list => { setProviders(list); if (list.length > 0) setSelected(list[0]!.id) })
      .catch(() => setMsg({ ok: false, text: '加载服务商失败' }))
  }, [])

  const loadConfig = (id: string) => {
    setMsg(null); setApiKey(''); setMaskedKey(null)
    const meta = providers.find(p => p.id === id)
    api.getProviderConfig(id)
      .then((cfg: LlmProvider) => {
        const c = cfg && cfg.providerId ? cfg : null
        setMaskedKey(c?.apiKey ?? null)
        setBaseUrl(c?.baseUrl ?? '')
        setModel(c?.defaultModel ?? meta?.models[0]?.id ?? '')
        setEnabled(c?.enabled ?? false)
        if (!c?.apiKey) {
          setMsg({ ok: false, text: '尚未配置有效 API Key。请粘贴智谱密钥（不是网址）后保存。' })
        } else if (c.baseUrl && c.baseUrl.length > 8) {
          const urlMask = c.baseUrl.slice(0, 3) + '••••' + c.baseUrl.slice(-4)
          if (c.apiKey === urlMask) {
            setMsg({ ok: false, text: '当前密钥疑似是 Base URL。请重新粘贴真正的 API Key。' })
          }
        }
      })
      .catch(() => {
        setBaseUrl(''); setModel(meta?.models[0]?.id ?? ''); setEnabled(false)
      })
  }

  useEffect(() => { if (selected) loadConfig(selected) /* eslint-disable-line react-hooks/exhaustive-deps */ }, [selected])

  const looksLikeUrl = (v: string) => /^https?:\/\//i.test(v.trim())

  const save = async () => {
    if (!selected) return
    setSaving(true); setMsg(null)
    // 明文框：以 React state 为准；再兜底读 DOM
    const keyToSave = (apiKey.trim() || apiKeyRef.current?.value?.trim() || '')
    const urlToSave = baseUrl.trim()
    if (!keyToSave && !maskedKey) {
      setMsg({ ok: false, text: '请先填写 API Key' })
      setSaving(false)
      return
    }
    if (keyToSave && looksLikeUrl(keyToSave)) {
      setMsg({ ok: false, text: `API Key 不能是网址（当前像：${keyToSave.slice(0, 32)}…）。请填智谱密钥。` })
      setSaving(false)
      return
    }
    if (keyToSave && urlToSave && keyToSave === urlToSave) {
      setMsg({ ok: false, text: 'API Key 与 Base URL 相同，说明填错栏了。' })
      setSaving(false)
      return
    }
    try {
      const saved = await api.saveProviderConfig(selected, {
        apiKey: keyToSave || undefined,
        baseUrl: urlToSave || undefined,
        defaultModel: model,
        enabled,
      })
      const mask = saved?.apiKey ?? null
      setMaskedKey(mask)
      setMsg({
        ok: true,
        text: keyToSave
          ? `已保存。回显：${mask ?? '（无）'} — 若仍像 htt••••opic 说明又存成了网址`
          : '已保存（未改密钥）',
      })
      setApiKey('')
    } catch (e) {
      setMsg({ ok: false, text: e instanceof ApiError ? e.message : 'Save failed' })
    } finally {
      setSaving(false)
    }
  }

  const testConn = async () => {
    if (!selected) return
    setTesting(true); setMsg(null)
    try {
      await api.testProviderConnection(selected)
      setMsg({ ok: true, text: '连接成功' })
    } catch (e) {
      setMsg({ ok: false, text: e instanceof ApiError ? e.message : '连接失败' })
    } finally {
      setTesting(false)
    }
  }

  const meta = providers.find(p => p.id === selected)

  return (
    <div className="p-6 max-w-2xl">
      <h1 className="text-lg font-semibold mb-4" style={{ color: 'var(--color-text)' }}>LLM 服务商</h1>

      <div className="flex gap-2 mb-4">
        {providers.map(p => (
          <button
            key={p.id}
            type="button"
            onClick={() => setSelected(p.id)}
            className="px-3 py-1.5 text-sm rounded-lg transition-colors"
            style={{
              backgroundColor: selected === p.id ? 'var(--color-accent)' : 'var(--color-bg-secondary)',
              color: selected === p.id ? '#fff' : 'var(--color-text)',
            }}
          >
            {p.name}
          </button>
        ))}
      </div>

      {meta && (
        <form
          className="p-4 rounded-lg border space-y-3"
          style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg-secondary)' }}
          autoComplete="off"
          onSubmit={e => { e.preventDefault(); void save() }}
        >
          <div className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>
            {maskedKey ? `已配置密钥：${maskedKey}` : '未配置 API Key'}
          </div>

          <label className="block">
            <span className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>
              API Key（明文可核对；保存后加密存储）{maskedKey ? ' — 留空则保留当前' : ''}
            </span>
            <div className="flex gap-2 mt-1">
              <input
                ref={apiKeyRef}
                type={hideKey ? 'password' : 'text'}
                name="tepeu-llm-api-key"
                autoComplete="off"
                spellCheck={false}
                value={apiKey}
                onChange={e => setApiKey(e.target.value)}
                placeholder="粘贴智谱 API Key，不要填 https://..."
                className="flex-1 px-3 py-2 text-sm rounded border font-mono"
                style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg)', color: 'var(--color-text)' }}
              />
              <button
                type="button"
                onClick={() => setHideKey(v => !v)}
                className="px-3 py-2 text-xs rounded border"
                style={{ borderColor: 'var(--color-border)', color: 'var(--color-text)' }}
              >
                {hideKey ? '显示' : '隐藏'}
              </button>
            </div>
          </label>

          <label className="block">
            <span className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>Base URL（可选）</span>
            <input
              type="text"
              name="tepeu-llm-base-url"
              autoComplete="off"
              value={baseUrl}
              onChange={e => setBaseUrl(e.target.value)}
              placeholder={selected === 'ollama' ? 'http://localhost:11434' : 'https://open.bigmodel.cn/api/anthropic'}
              className="w-full mt-1 px-3 py-2 text-sm rounded border font-mono"
              style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg)', color: 'var(--color-text)' }}
            />
          </label>

          <label className="block">
            <span className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>默认模型</span>
            <input
              value={model}
              onChange={e => setModel(e.target.value)}
              placeholder={meta?.models[0]?.id || 'glm-5.2'}
              list="model-suggestions"
              name="tepeu-llm-model"
              autoComplete="off"
              className="w-full mt-1 px-3 py-2 text-sm rounded border"
              style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg)', color: 'var(--color-text)' }}
            />
            <datalist id="model-suggestions">
              {meta?.models.map(m => <option key={m.id} value={m.id} label={m.name} />)}
            </datalist>
          </label>

          <label className="flex items-center gap-2 text-sm" style={{ color: 'var(--color-text)' }}>
            <input type="checkbox" checked={enabled} onChange={e => setEnabled(e.target.checked)} />
            启用
          </label>

          {msg && (
            <div className={`text-sm ${msg.ok ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>{msg.text}</div>
          )}

          <div className="flex gap-2">
            <button
              type="submit"
              disabled={saving}
              className="px-4 py-2 text-sm rounded disabled:opacity-50"
              style={{ backgroundColor: 'var(--color-accent)', color: '#fff' }}
            >
              {saving ? '保存中…' : '保存'}
            </button>
            <button
              type="button"
              onClick={testConn}
              disabled={testing}
              className="px-4 py-2 text-sm rounded disabled:opacity-50"
              style={{ backgroundColor: 'var(--color-bg-tertiary)', color: 'var(--color-text)' }}
            >
              {testing ? '测试中…' : '测试连接'}
            </button>
          </div>
        </form>
      )}

      <p className="mt-4 text-xs" style={{ color: 'var(--color-text-secondary)' }}>
        智谱 Anthropic 兼容：Base URL = <code>https://open.bigmodel.cn/api/anthropic</code>，模型 = <code>glm-5.2</code>。
        API Key 栏只能是密钥本身；若回显 <code>htt••••opic</code> 说明又存成了网址。
      </p>
    </div>
  )
}
