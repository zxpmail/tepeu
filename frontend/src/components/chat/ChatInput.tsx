/**
 * 聊天输入框 — pi-web 风格卡片 composer：auto-resize、IME、@ / 向上弹出。
 * / 与 @ 均可点选已安装技能；选中技能写入输入框，发送后由后端注入。
 * 关联：api.listFiles、api.listSkills、ChatView。
 */
import {
  useState, useEffect, useRef, useCallback,
  type KeyboardEvent, type ChangeEvent, type CompositionEvent,
} from 'react'
import { api } from '../../api/client'
import type { FileItem, Skill } from '../../types'

const UI_SLASH_COMMANDS = [
  { name: 'clear', label: '清空对话', kind: 'ui' as const },
  { name: 'new', label: '新建会话', kind: 'ui' as const },
  { name: 'files', label: '打开文件面板', kind: 'ui' as const },
]

type SlashItem =
  | { name: string; label: string; kind: 'ui' }
  | { name: string; label: string; kind: 'skill'; slug: string }

type AtItem =
  | { kind: 'skill'; key: string; label: string; insert: string }
  | { kind: 'file'; key: string; label: string; insert: string; file: FileItem }

const COMPOSITION_END_GRACE_MS = 100
const TEXTAREA_MAX_PX = 200

interface ChatInputProps {
  value: string
  onChange: (value: string) => void
  onSend: () => void
  onStop: () => void
  streaming: boolean
  disabled?: boolean
  placeholder?: string
  workspaceId?: string
  onSlashCommand?: (cmd: string) => void
  /** 底栏服务商选择 */
  providers?: { id: string; name: string }[]
  provider?: string
  onProviderChange?: (id: string) => void
}

export default function ChatInput({
  value,
  onChange,
  onSend,
  onStop,
  streaming,
  disabled = false,
  placeholder,
  workspaceId,
  onSlashCommand,
  providers,
  provider,
  onProviderChange,
}: ChatInputProps) {
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const composingRef = useRef(false)
  const compositionEndAt = useRef(0)
  const [atQuery, setAtQuery] = useState<string | null>(null)
  const [atItems, setAtItems] = useState<AtItem[]>([])
  const [atIndex, setAtIndex] = useState(0)
  const [slashOpen, setSlashOpen] = useState(false)
  const [slashIndex, setSlashIndex] = useState(0)
  const [skills, setSkills] = useState<Skill[]>([])
  const fileCacheRef = useRef<FileItem[] | null>(null)

  /** 按内容自动增高 */
  const resizeTextarea = useCallback(() => {
    const el = textareaRef.current
    if (!el) return
    el.style.height = 'auto'
    el.style.height = `${Math.min(el.scrollHeight, TEXTAREA_MAX_PX)}px`
  }, [])

  useEffect(() => {
    resizeTextarea()
  }, [value, resizeTextarea])

  useEffect(() => {
    if (!workspaceId) {
      setSkills([])
      return
    }
    let cancelled = false
    void api.listSkills(workspaceId).then(list => {
      if (!cancelled) setSkills(list)
    }).catch(() => {
      if (!cancelled) setSkills([])
    })
    return () => { cancelled = true }
  }, [workspaceId])

  const detectAtQuery = useCallback((text: string, cursor: number) => {
    const before = text.slice(0, cursor)
    const m = before.match(/@(\S*)$/)
    return m ? m[1]! : null
  }, [])

  const detectSlash = useCallback((text: string) => {
    return /^\/\S*$/.test(text.trim()) || text === '/'
  }, [])

  const slashItems: SlashItem[] = [
    ...UI_SLASH_COMMANDS,
    ...[...skills]
      .sort((a, b) => Number(b.enabled) - Number(a.enabled) || a.slug.localeCompare(b.slug))
      .map(s => ({
        name: s.slug,
        label: s.description?.trim() || s.name,
        kind: 'skill' as const,
        slug: s.slug,
      })),
  ]

  const filteredSlash = slashItems.filter(c => {
    const q = value.trim().slice(1).toLowerCase()
    return !q || c.name.toLowerCase().startsWith(q) || c.label.toLowerCase().includes(q)
  })

  const loadAtSuggestions = useCallback(async (query: string) => {
    const q = query.toLowerCase()
    const skillHits: AtItem[] = skills
      .filter(s =>
        !q
        || s.slug.toLowerCase().includes(q)
        || s.name.toLowerCase().includes(q)
        || (s.description ?? '').toLowerCase().includes(q),
      )
      .sort((a, b) => Number(b.enabled) - Number(a.enabled))
      .slice(0, 10)
      .map(s => ({
        kind: 'skill' as const,
        key: `skill:${s.slug}`,
        label: s.description?.trim() || s.name,
        insert: s.slug,
      }))

    try {
      if (!fileCacheRef.current) {
        const data = await api.listFiles('/', workspaceId)
        fileCacheRef.current = data.items.filter(i => !i.isDirectory)
      }
      const fileHits: AtItem[] = fileCacheRef.current
        .filter(i =>
          !q || i.name.toLowerCase().includes(q) || (`/${i.name}`).toLowerCase().includes(q),
        )
        .slice(0, 15)
        .map(i => ({
          kind: 'file' as const,
          key: `file:${i.name}`,
          label: i.name,
          insert: i.name,
          file: i,
        }))
      setAtItems([...skillHits, ...fileHits].slice(0, 20))
      setAtIndex(0)
    } catch {
      setAtItems(skillHits)
      setAtIndex(0)
    }
  }, [workspaceId, skills])

  useEffect(() => {
    fileCacheRef.current = null
  }, [workspaceId])

  useEffect(() => {
    const el = textareaRef.current
    const cursor = el?.selectionStart ?? value.length
    const q = detectAtQuery(value, cursor)
    setAtQuery(q)
    if (q !== null) {
      void loadAtSuggestions(q)
      setSlashOpen(false)
    } else {
      setAtItems([])
    }
    setSlashOpen(detectSlash(value))
  }, [value, detectAtQuery, detectSlash, loadAtSuggestions])

  const insertAtToken = (item: AtItem) => {
    const el = textareaRef.current
    const cursor = el?.selectionStart ?? value.length
    const before = value.slice(0, cursor)
    const after = value.slice(cursor)
    const replaced = before.replace(/@(\S*)$/, `@${item.insert} `)
    onChange(replaced + after)
    setAtQuery(null)
    setAtItems([])
    requestAnimationFrame(() => {
      const pos = replaced.length
      el?.setSelectionRange(pos, pos)
      el?.focus()
    })
  }

  const insertSkillSlash = (slug: string) => {
    onChange(`/${slug} `)
    setSlashOpen(false)
    requestAnimationFrame(() => {
      const el = textareaRef.current
      const pos = `/${slug} `.length
      el?.setSelectionRange(pos, pos)
      el?.focus()
    })
  }

  const runUiSlash = (name: string) => {
    onSlashCommand?.(name)
    onChange('')
    setSlashOpen(false)
  }

  const pickSlash = (item: SlashItem) => {
    if (item.kind === 'ui') {
      runUiSlash(item.name)
    } else {
      insertSkillSlash(item.slug)
    }
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    // 中文组字期间不触发 Enter 发送
    if (e.key === 'Enter' && !e.shiftKey) {
      if (composingRef.current || e.nativeEvent.isComposing) return
      if (Date.now() - compositionEndAt.current < COMPOSITION_END_GRACE_MS) return
    }

    if (atQuery !== null && atItems.length > 0) {
      if (e.key === 'ArrowDown') {
        e.preventDefault()
        setAtIndex(i => (i + 1) % atItems.length)
        return
      }
      if (e.key === 'ArrowUp') {
        e.preventDefault()
        setAtIndex(i => (i - 1 + atItems.length) % atItems.length)
        return
      }
      if (e.key === 'Enter' || e.key === 'Tab') {
        e.preventDefault()
        const item = atItems[atIndex]
        if (item) insertAtToken(item)
        return
      }
      if (e.key === 'Escape') {
        e.preventDefault()
        setAtQuery(null)
        setAtItems([])
        return
      }
    }

    if (slashOpen && filteredSlash.length > 0) {
      if (e.key === 'ArrowDown') {
        e.preventDefault()
        setSlashIndex(i => (i + 1) % filteredSlash.length)
        return
      }
      if (e.key === 'ArrowUp') {
        e.preventDefault()
        setSlashIndex(i => (i - 1 + filteredSlash.length) % filteredSlash.length)
        return
      }
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault()
        const exact = value.trim().slice(1)
        const match = filteredSlash.find(c => c.name === exact) ?? filteredSlash[slashIndex]
        if (match) pickSlash(match)
        return
      }
      if (e.key === 'Escape') {
        e.preventDefault()
        setSlashOpen(false)
        return
      }
    }

    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      if (!value.trim() || disabled) return
      onSend()
    }
  }

  const handleChange = (e: ChangeEvent<HTMLTextAreaElement>) => {
    onChange(e.target.value)
  }

  const onCompositionStart = () => {
    composingRef.current = true
  }

  const onCompositionEnd = (_e: CompositionEvent<HTMLTextAreaElement>) => {
    composingRef.current = false
    compositionEndAt.current = Date.now()
  }

  const popupStyle = {
    borderColor: 'var(--color-border)',
    backgroundColor: 'var(--color-bg)',
    color: 'var(--color-text)',
    boxShadow: '0 8px 24px -8px rgba(15,23,42,0.25)',
  } as const

  return (
    <div className="relative">
      {/* @ 向上弹出：技能 + 文件 */}
      {atQuery !== null && atItems.length > 0 && (
        <div
          className="absolute left-0 right-0 max-h-48 overflow-auto rounded-lg border text-sm z-20"
          style={{ ...popupStyle, bottom: 'calc(100% + 8px)' }}
        >
          {atItems.map((item, i) => (
            <button
              key={item.key}
              type="button"
              className="w-full text-left px-3 py-1.5 flex justify-between gap-2"
              style={{
                backgroundColor: i === atIndex ? 'var(--color-bg-selected)' : 'transparent',
                fontFamily: 'var(--font-mono)',
                fontSize: 12,
              }}
              onMouseDown={e => { e.preventDefault(); insertAtToken(item) }}
            >
              <span>@{item.insert}</span>
              <span className="text-xs truncate" style={{ color: 'var(--color-text-dim)', fontFamily: 'inherit' }}>
                {item.kind === 'skill' ? `技能 · ${item.label}` : '文件'}
              </span>
            </button>
          ))}
        </div>
      )}

      {/* / 向上弹出：界面命令 + 技能 */}
      {slashOpen && filteredSlash.length > 0 && (
        <div
          className="absolute left-0 w-80 max-h-56 overflow-auto rounded-lg border text-sm z-20"
          style={{ ...popupStyle, bottom: 'calc(100% + 8px)' }}
        >
          {filteredSlash.map((cmd, i) => (
            <button
              key={`${cmd.kind}:${cmd.name}`}
              type="button"
              className="w-full text-left px-3 py-1.5 flex justify-between gap-2"
              style={{
                backgroundColor: i === slashIndex ? 'var(--color-bg-selected)' : 'transparent',
              }}
              onMouseDown={e => { e.preventDefault(); pickSlash(cmd) }}
            >
              <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12 }}>/{cmd.name}</span>
              <span className="text-xs truncate" style={{ color: 'var(--color-text-dim)' }}>
                {cmd.kind === 'skill' ? `技能 · ${cmd.label}` : cmd.label}
              </span>
            </button>
          ))}
        </div>
      )}

      <div className={`chat-composer-card ${streaming ? 'streaming' : ''}`}>
        <div className="flex items-end gap-2 px-3 pt-2.5 pb-2">
          <textarea
            ref={textareaRef}
            value={value}
            onChange={handleChange}
            onKeyDown={handleKeyDown}
            onCompositionStart={onCompositionStart}
            onCompositionEnd={onCompositionEnd}
            placeholder={placeholder}
            rows={1}
            disabled={disabled}
            className="flex-1 resize-none border-0 outline-none bg-transparent py-1.5"
            style={{
              color: 'var(--color-text)',
              fontSize: 14,
              lineHeight: 1.6,
              maxHeight: TEXTAREA_MAX_PX,
              minHeight: 24,
            }}
          />
          <div className="flex gap-1.5 shrink-0 pb-0.5">
            {streaming && (
              <button
                type="button"
                onClick={onStop}
                className="px-2.5 py-1.5 text-xs rounded-md border"
                style={{
                  borderColor: 'color-mix(in srgb, var(--color-danger) 40%, var(--color-border))',
                  color: 'var(--color-danger)',
                  backgroundColor: 'transparent',
                }}
              >
                停止
              </button>
            )}
            <button
              type="button"
              onClick={onSend}
              disabled={!value.trim() || disabled}
              className="px-3 py-1.5 text-xs rounded-md disabled:opacity-40"
              style={{ backgroundColor: 'var(--color-accent)', color: '#fff' }}
            >
              {streaming ? '排队' : '发送'}
            </button>
          </div>
        </div>
        <div
          className="px-3 pb-2 text-[11px] flex items-center gap-2 flex-wrap"
          style={{ color: 'var(--color-text-dim)' }}
        >
          {providers && providers.length > 0 && onProviderChange && (
            <select
              value={provider ?? ''}
              onChange={e => onProviderChange(e.target.value)}
              className="chat-select text-[11px] py-0.5"
              disabled={disabled}
            >
              {providers.map(p => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
          )}
          <span className="opacity-70">Enter 发送</span>
          <span className="opacity-70">⇧Enter 换行</span>
          <span className="opacity-70">/ 技能或命令</span>
          <span className="opacity-70">@ 技能或文件</span>
        </div>
      </div>
    </div>
  )
}
