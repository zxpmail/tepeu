import { useState, useEffect } from 'react'
import { api, ApiError } from '../../api/client'
import type { ProviderMetadata } from '../../types'

interface SetupWizardProps {
  onComplete: () => void
}

/**
 * First-launch setup wizard.
 *
 * Guides the user through:
 * 1. LLM provider API key configuration
 * 2. Default workspace creation
 * 3. Welcome message with getting-started tips
 *
 * Shown only when no LLM providers have an enabled API key configured.
 */
export default function SetupWizard({ onComplete }: SetupWizardProps) {
  const [step, setStep] = useState(0)
  const [providers, setProviders] = useState<ProviderMetadata[]>([])
  const [selected, setSelected] = useState('')
  const [apiKey, setApiKey] = useState('')
  const [baseUrl, setBaseUrl] = useState('')
  const [model, setModel] = useState('')
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState(false)
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null)
  const [wsName, setWsName] = useState('')
  const [creating, setCreating] = useState(false)

  useEffect(() => {
    api.getAvailableProviders()
      .then(list => {
        setProviders(list)
        if (list.length > 0) setSelected(list[0]!.id)
      })
      .catch(() => {})
  }, [])

  const meta = providers.find(p => p.id === selected)

  // Step 1: API Key configuration
  const handleSaveAndTest = async () => {
    if (!selected || !apiKey.trim()) return
    setSaving(true)
    setMsg(null)
    try {
      await api.saveProviderConfig(selected, {
        apiKey: apiKey.trim(),
        baseUrl: baseUrl || undefined,
        defaultModel: model || meta?.models[0]?.id,
        enabled: true,
      })
      // Test the connection
      setTesting(true)
      try {
        await api.testProviderConnection(selected)
        setMsg({ ok: true, text: 'Key saved and connection verified!' })
        setApiKey('')
        setTimeout(() => { setStep(1); setMsg(null) }, 1200)
      } catch {
        setMsg({ ok: true, text: 'Key saved but connection test failed — you can fix this later in ⚙ Provider settings.' })
        setTimeout(() => { setStep(1); setMsg(null) }, 2000)
      }
    } catch (e) {
      setMsg({ ok: false, text: e instanceof ApiError ? e.message : 'Save failed' })
    } finally {
      setSaving(false)
      setTesting(false)
    }
  }

  // Step 2: Create default workspace
  const handleCreateWorkspace = async () => {
    const name = wsName.trim() || 'My Workspace'
    setCreating(true)
    try {
      await api.createWorkspace(name, 'Default workspace created by setup wizard')
      setMsg({ ok: true, text: `Workspace "${name}" created!` })
      setTimeout(() => { setStep(2); setMsg(null) }, 1000)
    } catch (e) {
      setMsg({ ok: false, text: e instanceof ApiError ? e.message : 'Failed to create workspace' })
    } finally {
      setCreating(false)
    }
  }

  const handleSkipWorkspace = () => {
    setStep(2)
  }

  // Step 3: Done
  const handleFinish = () => {
    onComplete()
  }

  return (
    <div className="h-screen flex items-center justify-center" style={{ backgroundColor: 'var(--color-bg)' }}>
      <div className="w-full max-w-lg mx-auto p-8">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="text-4xl mb-2">🚀</div>
          <h1 className="text-2xl font-bold" style={{ color: 'var(--color-text)' }}>Tepeu</h1>
          <p className="text-sm mt-1" style={{ color: 'var(--color-text-secondary)' }}>智能体操作系统 — v0.1.0</p>
        </div>

        {/* Step indicator */}
        <div className="flex items-center justify-center gap-2 mb-8">
          {['API Key', '工作区', '开始'].map((label, i) => (
            <div key={i} className="flex items-center gap-2">
              <div className={`w-7 h-7 rounded-full flex items-center justify-center text-xs font-medium ${
                i < step ? 'bg-green-500 text-white' :
                i === step ? 'bg-blue-500 text-white' :
                'bg-gray-200 text-gray-500 dark:bg-gray-700 dark:text-gray-400'
              }`}>
                {i < step ? '✓' : i + 1}
              </div>
              <span className={`text-xs ${i === step ? 'font-medium' : ''}`} style={{ color: i === step ? 'var(--color-text)' : 'var(--color-text-secondary)' }}>
                {label}
              </span>
              {i < 2 && <span className="text-xs" style={{ color: 'var(--color-border)' }}>—</span>}
            </div>
          ))}
        </div>

        {/* Step 0: API Key */}
        {step === 0 && (
          <div className="space-y-4">
            <h2 className="text-lg font-semibold" style={{ color: 'var(--color-text)' }}>配置 AI 服务商</h2>
            <p className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>
              Tepeu 需要 LLM API Key 来驱动智能体对话。选择一个服务商并输入你的密钥。
            </p>

            {/* Provider selector */}
            <div className="flex gap-2">
              {providers.map(p => (
                <button
                  key={p.id}
                  onClick={() => { setSelected(p.id); setMsg(null) }}
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

            {/* API Key input */}
            <div>
              <label className="text-xs block mb-1" style={{ color: 'var(--color-text-secondary)' }}>API Key</label>
              <input
                type="password"
                value={apiKey}
                onChange={e => setApiKey(e.target.value)}
                placeholder={selected === 'ollama' ? '（本地模型无需密钥）' : 'sk-...'}
                className="w-full px-3 py-2 text-sm rounded border"
                style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg)', color: 'var(--color-text)' }}
                onKeyDown={e => { if (e.key === 'Enter') handleSaveAndTest() }}
              />
            </div>

            {/* Base URL (optional, shown for Ollama) */}
            {selected === 'ollama' && (
              <div>
                <label className="text-xs block mb-1" style={{ color: 'var(--color-text-secondary)' }}>Base URL</label>
                <input
                  value={baseUrl}
                  onChange={e => setBaseUrl(e.target.value)}
                  placeholder="http://localhost:11434"
                  className="w-full px-3 py-2 text-sm rounded border"
                  style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg)', color: 'var(--color-text)' }}
                />
              </div>
            )}

            {/* Model selector (free text + predefined suggestions) */}
            {meta && (
              <div>
                <label className="text-xs block mb-1" style={{ color: 'var(--color-text-secondary)' }}>默认模型</label>
                <input
                  value={model}
                  onChange={e => setModel(e.target.value)}
                  placeholder={meta.models[0]?.id || '模型名称'}
                  list="wizard-model-suggestions"
                  className="w-full px-3 py-2 text-sm rounded border"
                  style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg)', color: 'var(--color-text)' }}
                />
                <datalist id="wizard-model-suggestions">
                  {meta.models.map(m => <option key={m.id} value={m.id} label={m.name} />)}
                </datalist>
              </div>
            )}

            {msg && (
              <div className={`text-sm ${msg.ok ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
                {msg.text}
              </div>
            )}

            <div className="flex gap-2">
              <button
                onClick={handleSaveAndTest}
                disabled={saving || !apiKey.trim()}
                className="px-5 py-2 text-sm rounded disabled:opacity-50"
                style={{ backgroundColor: 'var(--color-accent)', color: '#fff' }}
              >
                {saving || testing ? '设置中…' : '保存并继续'}
              </button>
              <button
                onClick={() => setStep(1)}
                className="px-3 py-2 text-sm rounded"
                style={{ color: 'var(--color-text-secondary)' }}
              >
                跳过（稍后配置）
              </button>
            </div>
          </div>
        )}

        {/* Step 1: Workspace */}
        {step === 1 && (
          <div className="space-y-4">
            <h2 className="text-lg font-semibold" style={{ color: 'var(--color-text)' }}>创建第一个工作区</h2>
            <p className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>
              工作区是存放你的项目、对话和记忆的地方。给它取个名字。
            </p>

            <div>
              <label className="text-xs block mb-1" style={{ color: 'var(--color-text-secondary)' }}>工作区名称</label>
              <input
                value={wsName}
                onChange={e => setWsName(e.target.value)}
                placeholder="我的工作区"
                className="w-full px-3 py-2 text-sm rounded border"
                style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg)', color: 'var(--color-text)' }}
                onKeyDown={e => { if (e.key === 'Enter') handleCreateWorkspace() }}
              />
            </div>

            {msg && (
              <div className={`text-sm ${msg.ok ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
                {msg.text}
              </div>
            )}

            <div className="flex gap-2">
              <button
                onClick={handleCreateWorkspace}
                disabled={creating}
                className="px-5 py-2 text-sm rounded disabled:opacity-50"
                style={{ backgroundColor: 'var(--color-accent)', color: '#fff' }}
              >
                {creating ? '创建中…' : '创建'}
              </button>
              <button
                onClick={handleSkipWorkspace}
                className="px-3 py-2 text-sm rounded"
                style={{ color: 'var(--color-text-secondary)' }}
              >
                跳过
              </button>
            </div>
          </div>
        )}

        {/* Step 2: Welcome */}
        {step === 2 && (
          <div className="space-y-4 text-center">
            <div className="text-5xl mb-4">🎉</div>
            <h2 className="text-xl font-semibold" style={{ color: 'var(--color-text)' }}>准备就绪！</h2>
            <p className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>
              Tepeu 已准备就绪。快速入门：
            </p>

            <div className="text-left space-y-3 p-4 rounded-lg" style={{ backgroundColor: 'var(--color-bg-secondary)' }}>
              <div className="flex items-start gap-3">
                <span className="text-lg shrink-0">💬</span>
                <div>
                  <div className="text-sm font-medium" style={{ color: 'var(--color-text)' }}>Agent 对话</div>
                  <div className="text-xs mt-0.5" style={{ color: 'var(--color-text-secondary)' }}>选择对话面板开始聊天。Agent 可以读取文件、浏览网页等。</div>
                </div>
              </div>
              <div className="flex items-start gap-3">
                <span className="text-lg shrink-0">📁</span>
                <div>
                  <div className="text-sm font-medium" style={{ color: 'var(--color-text)' }}>工作区项目</div>
                  <div className="text-xs mt-0.5" style={{ color: 'var(--color-text-secondary)' }}>每个工作区拥有独立的文件和记忆。创建多个工作区来组织工作。</div>
                </div>
              </div>
              <div className="flex items-start gap-3">
                <span className="text-lg shrink-0">🧠</span>
                <div>
                  <div className="text-sm font-medium" style={{ color: 'var(--color-text)' }}>白盒记忆</div>
                  <div className="text-xs mt-0.5" style={{ color: 'var(--color-text-secondary)' }}>所有记忆可见可编辑。追溯每条记忆的来源——没有黑盒。</div>
                </div>
              </div>
              <div className="flex items-start gap-3">
                <span className="text-lg shrink-0">⚙</span>
                <div>
                  <div className="text-sm font-medium" style={{ color: 'var(--color-text)' }}>服务商设置</div>
                  <div className="text-xs mt-0.5" style={{ color: 'var(--color-text-secondary)' }}>随时在 ⚙ 面板中添加或更换 API Key、切换模型、测试连接。</div>
                </div>
              </div>
            </div>

            <button
              onClick={handleFinish}
              className="px-6 py-2.5 text-sm rounded font-medium"
              style={{ backgroundColor: 'var(--color-accent)', color: '#fff' }}
            >
              开始使用 Tepeu
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
