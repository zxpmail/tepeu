import { useState, useEffect, useRef } from 'react'
import { useMemory } from '../../hooks/useMemory'
import type { Memory } from '../../types'

interface MemoryViewProps {
  workspaceId: string | undefined
}

export default function MemoryView({ workspaceId }: MemoryViewProps) {
  const {
    memories, loading, error, searchQuery, tags, hasMore,
    setSearchQuery, setTags, loadMemories,
    createMemory, updateMemory, deleteMemory,
  } = useMemory(workspaceId)

  const [selected, setSelected] = useState<Memory | null>(null)
  const [editContent, setEditContent] = useState('')
  const [editTags, setEditTags] = useState('')
  const [showCreate, setShowCreate] = useState(false)
  const [createContent, setCreateContent] = useState('')
  const [createTagInput, setCreateTagInput] = useState('')
  const [deleteConfirm, setDeleteConfirm] = useState<string | null>(null)
  const [tagFilterInput, setTagFilterInput] = useState('')

  // Load on mount & when workspace/search/tags change
  useEffect(() => {
    loadMemories(true)
  }, [workspaceId, searchQuery, tags])

  // Debounced search
  const [searchInput, setSearchInput] = useState('')
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  useEffect(() => {
    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => setSearchQuery(searchInput), 300)
    return () => { if (debounceRef.current) clearTimeout(debounceRef.current) }
  }, [searchInput])

  const handleSelect = (m: Memory) => {
    setSelected(m)
    setEditContent(m.content)
    setEditTags((m.tags || []).join(', '))
    setShowCreate(false)
  }

  const handleSave = async () => {
    if (!selected) return
    const tagList = editTags.split(',').map(t => t.trim()).filter(Boolean)
    await updateMemory(selected.id, editContent, tagList)
    setSelected(null)
  }

  const handleDelete = async (id: string) => {
    const ok = await deleteMemory(id)
    if (ok && selected?.id === id) setSelected(null)
    setDeleteConfirm(null)
  }

  const handleCreate = async () => {
    if (!workspaceId || !createContent.trim()) return
    const tagList = createTagInput.split(',').map(t => t.trim()).filter(Boolean)
    const m = await createMemory(workspaceId, createContent.trim(), tagList)
    if (m) { setShowCreate(false); setCreateContent(''); setCreateTagInput('') }
  }

  const addTagFilter = () => {
    const t = tagFilterInput.trim()
    if (t && !tags.includes(t)) setTags([...tags, t])
    setTagFilterInput('')
  }

  const formatDate = (d: string) => {
    try { return new Date(d).toLocaleString() } catch { return d }
  }

  const shortenSource = (s: string) => s.length > 28 ? s.slice(0, 25) + '…' : s

  if (!workspaceId) {
    return <div className="p-4" style={{ color: 'var(--color-text-secondary)' }}>请先选择或创建工作区。</div>
  }

  return (
    <div className="flex h-full">
      {/* Left panel: search + list */}
      <div className="flex-1 flex flex-col overflow-hidden" style={{ borderColor: 'var(--color-border)' }}>
        {/* Search bar */}
        <div className="p-3 border-b space-y-2" style={{ borderColor: 'var(--color-border)' }}>
          <div className="flex gap-2">
            <input
              value={searchInput}
              onChange={e => setSearchInput(e.target.value)}
              placeholder="搜索记忆…"
              className="flex-1 px-3 py-1.5 text-sm rounded border"
              style={{
                borderColor: 'var(--color-border)',
                backgroundColor: 'var(--color-bg)',
                color: 'var(--color-text)',
              }}
            />
            <button
              onClick={() => setShowCreate(!showCreate)}
              className="px-3 py-1.5 text-sm rounded"
              style={{ backgroundColor: 'var(--color-accent)', color: '#fff' }}
            >+ 新建</button>
          </div>
          {/* Tag filter */}
          <div className="flex items-center gap-1 flex-wrap">
            {tags.map(t => (
              <span key={t} className="text-xs px-2 py-0.5 rounded flex items-center gap-1"
                style={{ backgroundColor: 'var(--color-bg-tertiary)', color: 'var(--color-text)' }}>
                #{t}
                <button onClick={() => setTags(tags.filter(x => x !== t))} className="text-xs opacity-60 hover:opacity-100">×</button>
              </span>
            ))}
            <input
              value={tagFilterInput}
              onChange={e => setTagFilterInput(e.target.value)}
              onKeyDown={e => { if (e.key === 'Enter') addTagFilter() }}
              placeholder="按标签过滤…"
              className="text-xs px-2 py-0.5 rounded border flex-1 min-w-[80px]"
              style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg)', color: 'var(--color-text)' }}
            />
          </div>
        </div>

        {/* Create memory form */}
        {showCreate && (
          <div className="p-3 border-b" style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg-secondary)' }}>
            <textarea
              value={createContent}
              onChange={e => setCreateContent(e.target.value)}
              placeholder="记忆内容…"
              rows={3}
              className="w-full px-3 py-2 text-sm rounded border resize-none mb-2"
              style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg)', color: 'var(--color-text)' }}
            />
            <input
              value={createTagInput}
              onChange={e => setCreateTagInput(e.target.value)}
              placeholder="标签：逗号分隔"
              className="w-full px-3 py-1.5 text-sm rounded border mb-2"
              style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg)', color: 'var(--color-text)' }}
            />
            <div className="flex gap-2">
              <button onClick={handleCreate} className="px-3 py-1 text-sm rounded" style={{ backgroundColor: 'var(--color-accent)', color: '#fff' }}>
                Save
              </button>
              <button onClick={() => { setShowCreate(false); setCreateContent(''); setCreateTagInput('') }}
                className="px-3 py-1 text-sm rounded" style={{ backgroundColor: 'var(--color-bg-tertiary)', color: 'var(--color-text)' }}>
                取消
              </button>
            </div>
          </div>
        )}

        {/* Loading / Error */}
        {loading && memories.length === 0 && (
          <div className="p-4 text-sm" style={{ color: 'var(--color-text-secondary)' }}>加载中…</div>
        )}
        {error && (
          <div className="p-3 m-3 rounded text-sm border border-red-300 bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300">
            {error}
          </div>
        )}

        {/* Memory list */}
        <div className="flex-1 overflow-auto p-2 space-y-1">
          {!loading && memories.length === 0 && (
            <div className="p-8 text-center text-sm" style={{ color: 'var(--color-text-secondary)' }}>
              {searchInput || tags.length > 0 ? '没有匹配的记忆。' : '暂无记忆。开始对话将自动创建记忆。'}
            </div>
          )}
          {memories.map(m => (
            <button
              key={m.id}
              onClick={() => handleSelect(m)}
              className="w-full text-left px-3 py-2 rounded text-sm hover:opacity-80 transition-opacity"
              style={{
                backgroundColor: selected?.id === m.id ? 'var(--color-bg-tertiary)' : 'transparent',
                color: 'var(--color-text)',
              }}
            >
              <div className="line-clamp-2 whitespace-pre-wrap break-words">{m.content}</div>
              <div className="flex items-center gap-2 mt-1 text-xs" style={{ color: 'var(--color-text-secondary)' }}>
                <span>🔖 {shortenSource(m.source)}</span>
                {(m.tags || []).length > 0 && (
                  <span>{m.tags.map(t => `#${t}`).join(' ')}</span>
                )}
                <span className="ml-auto">{formatDate(m.createdAt)}</span>
              </div>
            </button>
          ))}
          {hasMore && (
            <button
              onClick={() => loadMemories(false)}
              disabled={loading}
              className="w-full text-center py-2 text-sm rounded"
              style={{ color: 'var(--color-text-secondary)' }}
            >
              {loading ? '加载中…' : '加载更多'}
            </button>
          )}
        </div>
      </div>

      {/* Right panel: detail / editor */}
      {selected && (
        <div className="w-1/2 border-l overflow-auto p-4 flex flex-col" style={{
          borderColor: 'var(--color-border)',
          backgroundColor: 'var(--color-panel-bg)',
        }}>
          <div className="flex items-center justify-between mb-3">
            <span className="text-sm font-medium" style={{ color: 'var(--color-text)' }}>
              编辑记忆
            </span>
            <button onClick={() => setSelected(null)}
              className="text-xs px-2 py-1 rounded" style={{ color: 'var(--color-text-secondary)' }}>
              ✕
            </button>
          </div>

          {/* Source trace */}
          <div className="mb-3 p-2 rounded text-xs" style={{ backgroundColor: 'var(--color-bg-secondary)', color: 'var(--color-text-secondary)' }}>
            <strong>来源：</strong> {selected.source}
            <br />
            <strong>ID:</strong> {selected.id}
            <br />
            <strong>创建时间：</strong> {formatDate(selected.createdAt)}
          </div>

          {/* Content editor */}
          <textarea
            value={editContent}
            onChange={e => setEditContent(e.target.value)}
            rows={8}
            className="w-full px-3 py-2 text-sm rounded border resize-none mb-3"
            style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg)', color: 'var(--color-text)' }}
          />

          {/* Tags editor */}
          <input
            value={editTags}
            onChange={e => setEditTags(e.target.value)}
            placeholder="标签：逗号分隔"
            className="w-full px-3 py-1.5 text-sm rounded border mb-3"
            style={{ borderColor: 'var(--color-border)', backgroundColor: 'var(--color-bg)', color: 'var(--color-text)' }}
          />

          {/* Actions */}
          <div className="flex items-center gap-2">
            <button onClick={handleSave} className="px-4 py-1.5 text-sm rounded" style={{ backgroundColor: 'var(--color-accent)', color: '#fff' }}>
              保存
            </button>
            {deleteConfirm === selected.id ? (
              <div className="flex items-center gap-2">
                <span className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>确认删除？</span>
                <button onClick={() => handleDelete(selected.id)}
                  className="px-3 py-1 text-sm rounded border border-red-300 text-red-600">
                  删除
                </button>
                <button onClick={() => setDeleteConfirm(null)}
                  className="px-3 py-1 text-sm rounded" style={{ backgroundColor: 'var(--color-bg-tertiary)', color: 'var(--color-text)' }}>
                  Cancel
                </button>
              </div>
            ) : (
              <button onClick={() => setDeleteConfirm(selected.id)}
                className="px-3 py-1 text-sm rounded ml-auto" style={{ color: '#dc2626' }}>
                删除
              </button>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
