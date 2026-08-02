/**
 * 文件版本历史：列表、回滚、与当前文件 DIFF。
 * 关联：FileBrowserView、RightFilePanel、api.getFileHistory。
 */
import { useEffect, useState } from 'react'
import { api, ApiError } from '../../api/client'
import type { FileVersion } from '../../types'
import FileDiff from './FileDiff'

interface VersionPanelProps {
  path: string
  workspaceId?: string
  onRestore: () => void
  onClose: () => void
}

export default function VersionPanel({ path, workspaceId, onRestore, onClose }: VersionPanelProps) {
  const [versions, setVersions] = useState<FileVersion[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [restoring, setRestoring] = useState<string | null>(null)
  const [diffing, setDiffing] = useState<string | null>(null)
  const [diffPayload, setDiffPayload] = useState<{
    oldLabel: string
    newLabel: string
    oldText: string
    newText: string
  } | null>(null)

  useEffect(() => {
    setLoading(true)
    setError(null)
    setDiffPayload(null)
    api.getFileHistory(path, workspaceId)
      .then((data) => setVersions(data.versions || []))
      .catch((e) => setError(e instanceof Error ? e.message : '加载版本失败'))
      .finally(() => setLoading(false))
  }, [path, workspaceId])

  const handleRestore = async (versionId: string) => {
    setRestoring(versionId)
    setError(null)
    try {
      await api.restoreFileVersion(versionId)
      onRestore()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : e instanceof Error ? e.message : '恢复失败')
    } finally {
      setRestoring(null)
    }
  }

  const handleDiff = async (v: FileVersion) => {
    setDiffing(v.id)
    setError(null)
    try {
      const [oldText, current] = await Promise.all([
        api.getVersionContent(v.id),
        api.readFile(path, workspaceId),
      ])
      setDiffPayload({
        oldLabel: `v${v.versionNo}`,
        newLabel: '当前',
        oldText,
        newText: current.content ?? '',
      })
    } catch (e) {
      setError(e instanceof Error ? e.message : '对比失败')
      setDiffPayload(null)
    } finally {
      setDiffing(null)
    }
  }

  const formatDate = (d: string) => {
    try { return new Date(d).toLocaleString() } catch { return d }
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between mb-3 shrink-0">
        <span className="text-sm font-medium" style={{ color: 'var(--color-text)' }}>
          版本历史：{path.split('/').pop()}
        </span>
        <button type="button" onClick={onClose} className="text-xs px-2 py-1 rounded" style={{ color: 'var(--color-text-secondary)' }}>✕</button>
      </div>
      {error && (
        <div className="text-xs mb-2 p-2 rounded" style={{ color: 'var(--color-danger)', backgroundColor: 'color-mix(in srgb, var(--color-danger) 10%, transparent)' }}>
          {error}
        </div>
      )}
      {diffPayload ? (
        <div className="flex-1 min-h-0 flex flex-col">
          <button
            type="button"
            className="text-xs mb-2 self-start"
            style={{ color: 'var(--color-accent)' }}
            onClick={() => setDiffPayload(null)}
          >
            ← 返回列表
          </button>
          <FileDiff {...diffPayload} />
        </div>
      ) : loading ? (
        <div className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>加载版本中…</div>
      ) : versions.length === 0 ? (
        <div className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>暂无版本历史。</div>
      ) : (
        <div className="flex-1 overflow-auto space-y-1">
          {versions.map(v => (
            <div key={v.id} className="flex items-center justify-between gap-2 p-2 rounded text-sm"
              style={{ backgroundColor: 'var(--color-bg-secondary)' }}>
              <div className="min-w-0">
                <span className="font-medium">v{v.versionNo}</span>
                <span className="ml-2 text-xs" style={{ color: 'var(--color-text-secondary)' }}>{formatDate(v.createdAt)}</span>
              </div>
              <div className="flex gap-1 shrink-0">
                <button
                  type="button"
                  onClick={() => void handleDiff(v)}
                  disabled={diffing === v.id}
                  className="text-xs px-2 py-1 rounded"
                  style={{ color: 'var(--color-text-secondary)' }}
                >
                  {diffing === v.id ? '对比中…' : '对比'}
                </button>
                <button
                  type="button"
                  onClick={() => void handleRestore(v.id)}
                  disabled={restoring === v.id}
                  className="text-xs px-2 py-1 rounded"
                  style={{ backgroundColor: 'var(--color-accent)', color: '#fff', opacity: restoring === v.id ? 0.6 : 1 }}
                >
                  {restoring === v.id ? '恢复中…' : '恢复'}
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
