import { useState, useEffect } from 'react'
import { api, ApiError } from '../../api/client'
import type { ProviderMetadata, LlmProvider } from '../../types'

/**
 * LLM Provider configuration (§7.4). Lets the user enter/rotate an API key, set the default
 * model, and toggle enable. Keys are stored encrypted (CryptoService) and echoed back masked,
 * so the key field is blank by default — leaving it blank on save keeps the existing key.
 */
export default function ProviderSettingsView() {
  const [providers, setProviders] = useState<ProviderMetadata[]>([])
  const [selected, setSelected] = useState('')
  const [apiKey, setApiKey] = useState('')
  const [baseUrl, setBaseUrl] = useState('')
  const [model, setModel] = useState('')
  const [enabled, setEnabled] = useState(false)
  const [maskedKey, setMaskedKey] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState(false)
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null)

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
      })
      .catch(() => {
        setBaseUrl(''); setModel(meta?.models[0]?.id ?? ''); setEnabled(false)
      })
  }

  useEffect(() => { if (selected) loadConfig(selected) /* eslint-disable-line react-hooks/exhaustive-deps */ }, [selected])

  const save = async () => {
    if (!selected) return
    setSaving(true); setMsg(null)
    try {
      await api.saveProviderConfig(selected, {
        apiKey: apiKey || undefined,  // blank = keep existing key
        baseUrl: baseUrl || undefined,
        defaultModel: model,
        enabled,
      })
      setMsg({ ok: true, text: 'Saved' })
      setApiKey('')
      loadConfig(selected)
    } catch (e) {
      setMsg({ ok: false, text: e instanceof ApiError ? e.message : 'Save failed' })
    } finally {
      setSaving(false)
    }
  }

  // Probe the saved credentials with one minimal round-trip (backend POST /api/provider/test/:id).
  // Tests the persisted config, so an unsaved key is not reflected — save first.
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

      {/* Provider tabs */}
      <div className="flex gap-2 mb-4">
        {providers.map(p => (
          <button
            key={p.id}
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
        <div className="p-4 rounded-lg border space-y-3" style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg-secondary)' }}>
          <div className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>
            {maskedKey ? `已配置密钥：${maskedKey}` : '未配置 API Key'}
          </div>

          <label className="block">
            <span className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>API Key {maskedKey && '（留空则保留当前）'}</span>
            <input
              type="password"
              value={apiKey}
              onChange={e => setApiKey(e.target.value)}
              placeholder={maskedKey ? '••••••••' : 'sk-...'}
              className="w-full mt-1 px-3 py-2 text-sm rounded border"
              style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg)', color: 'var(--color-text)' }}
            />
          </label>

          <label className="block">
            <span className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>Base URL（可选）</span>
            <input
              value={baseUrl}
              onChange={e => setBaseUrl(e.target.value)}
              placeholder={selected === 'ollama' ? 'http://localhost:11434' : 'https://api.example.com'}
              className="w-full mt-1 px-3 py-2 text-sm rounded border"
              style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg)', color: 'var(--color-text)' }}
            />
          </label>

          <label className="block">
            <span className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>默认模型</span>
            <div className="relative mt-1">
              <input
                value={model}
                onChange={e => setModel(e.target.value)}
                placeholder={meta?.models[0]?.id || 'model-name'}
                list="model-suggestions"
                className="w-full px-3 py-2 text-sm rounded border"
                style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg)', color: 'var(--color-text)' }}
              />
              <datalist id="model-suggestions">
                {meta?.models.map(m => <option key={m.id} value={m.id} label={m.name} />)}
              </datalist>
            </div>
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
              onClick={save}
              disabled={saving}
              className="px-4 py-2 text-sm rounded disabled:opacity-50"
              style={{ backgroundColor: 'var(--color-accent)', color: '#fff' }}
            >
              {saving ? '保存中…' : '保存'}
            </button>
            <button
              onClick={testConn}
              disabled={testing || !maskedKey}
              title={maskedKey ? '发送最小请求验证已保存的密钥' : '请先保存密钥'}
              className="px-4 py-2 text-sm rounded disabled:opacity-50"
              style={{ backgroundColor: 'var(--color-bg-tertiary)', color: 'var(--color-text)' }}
            >
              {testing ? '测试中…' : '测试连接'}
            </button>
          </div>
        </div>
      )}

      <p className="mt-4 text-xs" style={{ color: 'var(--color-text-secondary)' }}>
        API Key 使用 AES-256-GCM 加密存储，不会完整回显。保存后可使用<strong>测试连接</strong>验证密钥是否有效。
      </p>
    </div>
  )
}
