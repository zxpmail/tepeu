import { useState, useCallback, useEffect, useRef } from 'react'
import { api } from '../../api/client'
import { useWorkspaceEvents } from '../../context/WorkspaceEvents'
import type { FileItem, FileVersion } from '../../types'
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import json from 'highlight.js/lib/languages/json'
import python from 'highlight.js/lib/languages/python'
import java from 'highlight.js/lib/languages/java'
import bash from 'highlight.js/lib/languages/bash'
import markdown from 'highlight.js/lib/languages/markdown'
import 'highlight.js/styles/github.css'
import { marked } from 'marked'

// Register languages for highlight.js
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('css', css)
hljs.registerLanguage('json', json)
hljs.registerLanguage('python', python)
hljs.registerLanguage('java', java)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('markdown', markdown)

function detectLanguage(filename: string): string {
  const ext = filename.split('.').pop()?.toLowerCase() || ''
  const map: Record<string, string> = {
    ts: 'typescript', tsx: 'typescript', js: 'javascript', jsx: 'javascript',
    html: 'xml', htm: 'xml', svg: 'xml', css: 'css', scss: 'css',
    json: 'json', py: 'python', java: 'java',
    md: 'markdown', mkd: 'markdown',
    sh: 'bash', bash: 'bash', zsh: 'bash',
    yml: 'yaml', yaml: 'yaml',
  }
  return map[ext] || ''
}

function isImageFile(filename: string): boolean {
  return /\.(png|jpg|jpeg|gif|webp|svg|bmp)$/i.test(filename)
}

function isMarkdownFile(filename: string): boolean {
  return /\.(md|mkd|markdown)$/i.test(filename)
}

interface FilePreviewProps {
  path: string
  content: string
  mimeType: string
  onClose: () => void
}

function FilePreview({ path, content, mimeType, onClose }: FilePreviewProps) {
  const filename = path.split('/').pop() || path
  const codeRef = useRef<HTMLElement>(null)

  useEffect(() => {
    if (codeRef.current) {
      const lang = detectLanguage(filename)
      if (lang) codeRef.current.dataset.lang = lang
      hljs.highlightElement(codeRef.current)
    }
  }, [content, filename])

  // Image preview
  if (mimeType?.startsWith('image/') || isImageFile(filename)) {
    return (
      <div className="flex flex-col h-full">
        <div className="flex items-center justify-between mb-3 shrink-0">
          <span className="text-sm font-medium truncate" style={{ color: 'var(--color-text)' }}>{path}</span>
          <button onClick={onClose} className="text-xs px-2 py-1 rounded shrink-0" style={{ color: 'var(--color-text-secondary)' }}>✕</button>
        </div>
        <div className="flex-1 flex items-center justify-center overflow-auto bg-[#f0f0f0] dark:bg-[#111] rounded">
          <img src={`/api/files/preview/image?path=${encodeURIComponent(path)}`} alt={filename}
            className="max-w-full max-h-full object-contain" />
        </div>
      </div>
    )
  }

  // Markdown rendering
  if (mimeType?.includes('markdown') || isMarkdownFile(filename)) {
    const html = marked.parse(content) as string
    return (
      <div className="flex flex-col h-full">
        <div className="flex items-center justify-between mb-3 shrink-0">
          <span className="text-sm font-medium truncate" style={{ color: 'var(--color-text)' }}>{path}</span>
          <button onClick={onClose} className="text-xs px-2 py-1 rounded shrink-0" style={{ color: 'var(--color-text-secondary)' }}>✕</button>
        </div>
        <div
          className="flex-1 overflow-auto p-4 rounded prose prose-sm dark:prose-invert max-w-none"
          style={{ backgroundColor: 'var(--color-bg-secondary)', color: 'var(--color-text)' }}
          dangerouslySetInnerHTML={{ __html: html }}
        />
      </div>
    )
  }

  // Code / text
  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between mb-3 shrink-0">
        <span className="text-sm font-medium truncate" style={{ color: 'var(--color-text)' }}>{path}</span>
        <button onClick={onClose} className="text-xs px-2 py-1 rounded shrink-0" style={{ color: 'var(--color-text-secondary)' }}>✕</button>
      </div>
      <pre className="flex-1 overflow-auto text-sm p-4 rounded" style={{ backgroundColor: 'var(--color-bg-secondary)' }}>
        <code ref={codeRef} className="hljs">{content}</code>
      </pre>
    </div>
  )
}

interface VersionPanelProps {
  path: string
  onRestore: () => void
  onClose: () => void
}

function VersionPanel({ path, onRestore, onClose }: VersionPanelProps) {
  const [versions, setVersions] = useState<FileVersion[]>([])
  const [loading, setLoading] = useState(true)
  const [restoring, setRestoring] = useState<string | null>(null)

  useEffect(() => {
    setLoading(true)
    api.getFileHistory(path)
      .then((data: { path: string; versions: FileVersion[] }) => setVersions(data.versions || []))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [path])

  const handleRestore = async (versionId: string) => {
    setRestoring(versionId)
    try {
      await api.restoreFileVersion(versionId)
      onRestore()
    } catch { /* ignore */ }
    setRestoring(null)
  }

  const formatDate = (d: string) => {
    try { return new Date(d).toLocaleString() } catch { return d }
  }

  return (
    <div className="flex flex-col h-full">
      <div className="flex items-center justify-between mb-3 shrink-0">
        <span className="text-sm font-medium" style={{ color: 'var(--color-text)' }}>版本历史：{path.split('/').pop()}</span>
        <button onClick={onClose} className="text-xs px-2 py-1 rounded" style={{ color: 'var(--color-text-secondary)' }}>✕</button>
      </div>
      {loading ? (
        <div className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>加载版本中…</div>
      ) : versions.length === 0 ? (
        <div className="text-sm" style={{ color: 'var(--color-text-secondary)' }}>暂无版本历史。</div>
      ) : (
        <div className="flex-1 overflow-auto space-y-1">
          {versions.map(v => (
            <div key={v.id} className="flex items-center justify-between p-2 rounded text-sm"
              style={{ backgroundColor: 'var(--color-bg-secondary)' }}>
              <div>
                <span className="font-medium">v{v.versionNo}</span>
                <span className="ml-2 text-xs" style={{ color: 'var(--color-text-secondary)' }}>{formatDate(v.createdAt)}</span>
              </div>
              <button
                onClick={() => handleRestore(v.id)}
                disabled={restoring === v.id}
                className="text-xs px-2 py-1 rounded"
                style={{ backgroundColor: 'var(--color-accent)', color: '#fff', opacity: restoring === v.id ? 0.6 : 1 }}
              >
                {restoring === v.id ? '恢复中…' : '恢复'}
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

interface FileBrowserViewProps {
  fileBrowser: {
    files: FileItem[]
    currentPath: string
    loading: boolean
    error: string | null
    navigateTo: (path: string) => void
    loadFiles: (path?: string) => void
  }
  workspaceId?: string
}

export default function FileBrowserView({ fileBrowser, workspaceId }: FileBrowserViewProps) {
  const { files, currentPath, loading, error, navigateTo, loadFiles } = fileBrowser
  const { subscribe } = useWorkspaceEvents()
  const [previewFile, setPreviewFile] = useState<{ path: string; content: string; mimeType: string } | null>(null)
  const [showVersions, setShowVersions] = useState<string | null>(null)
  const [dragOver, setDragOver] = useState(false)
  const [uploading, setUploading] = useState(false)
  const previewPathRef = useRef<string | null>(null)

  // 保持预览路径 ref，供 file_changed 回调比对
  useEffect(() => {
    previewPathRef.current = previewFile?.path ?? null
  }, [previewFile?.path])

  // 订阅 agent 写文件事件：若正在预览该文件则重新读取
  useEffect(() => {
    return subscribe((path: string) => {
      const current = previewPathRef.current
      if (!current) return
      const norm = (p: string) => p.replace(/\\/g, '/').replace(/^\/+/, '')
      if (norm(current) !== norm(path) && current !== path) return
      api.readFile(current, workspaceId)
        .then(data => setPreviewFile(data))
        .catch(() => { /* 刷新失败忽略 */ })
    })
  }, [subscribe, workspaceId])

  const handleItemClick = async (item: FileItem) => {
    if (item.isDirectory) {
      const newPath = currentPath === '/' ? `/${item.name}` : `${currentPath}/${item.name}`
      navigateTo(newPath)
    } else {
      try {
        const path = currentPath === '/' ? `/${item.name}` : `${currentPath}/${item.name}`
        setShowVersions(null)
        const data = await api.readFile(path, workspaceId)
        setPreviewFile(data)
      } catch {
        // ApiError is thrown
      }
    }
  }

  // Drag-and-drop upload
  const handleDragOver = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    setDragOver(true)
  }, [])

  const handleDragLeave = useCallback((e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    setDragOver(false)
  }, [])

  const handleDrop = useCallback(async (e: React.DragEvent) => {
    e.preventDefault()
    e.stopPropagation()
    setDragOver(false)
    const fileList = Array.from(e.dataTransfer.files)
    if (fileList.length === 0) return
    setUploading(true)
    try {
      for (const file of fileList) {
        await api.uploadFile(file, currentPath)
      }
      loadFiles(currentPath)
    } catch {
      // ApiError
    }
    setUploading(false)
  }, [currentPath, loadFiles])

  if (loading) {
    return <div className="p-4" style={{ color: 'var(--color-text-secondary)' }}>加载中...</div>
  }

  if (error) {
    return (
      <div className="p-4">
        <div className="p-3 rounded text-sm border border-red-300 bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300">
          {error}
        </div>
      </div>
    )
  }

  return (
    <div
      className="flex h-full relative"
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
    >
      {/* File tree */}
      <div className="flex-1 p-4 overflow-auto relative" style={{ borderColor: 'var(--color-border)' }}>
        {/* Breadcrumb */}
        <div className="flex items-center gap-1 mb-3 text-sm" style={{ color: 'var(--color-text-secondary)' }}>
          <button onClick={() => navigateTo('/')} className="hover:underline">~</button>
          {currentPath.split('/').filter(Boolean).map((segment, i, arr) => (
            <span key={i}>
              <span className="mx-1">/</span>
              <button
                onClick={() => {
                  const path = '/' + arr.slice(0, i + 1).join('/')
                  navigateTo(path)
                }}
                className="hover:underline"
              >
                {segment}
              </button>
            </span>
          ))}
        </div>

        {/* Empty state */}
        {files.length === 0 && (
          <div className="p-8 text-center text-sm" style={{ color: 'var(--color-text-secondary)' }}>
            空目录
          </div>
        )}

        {/* File list */}
        <div className="space-y-0.5">
          <button
            onClick={() => {
              const parent = currentPath.split('/').slice(0, -1).join('/') || '/'
              navigateTo(parent)
            }}
            className="flex items-center gap-2 px-2 py-1 text-sm rounded hover:opacity-80 w-full text-left"
            style={{ color: 'var(--color-text-secondary)' }}
            disabled={currentPath === '/'}
          >
            📁 ..
          </button>
          {files.map((item, i) => (
            <div key={i} className="flex items-center gap-1 group">
              <button
                onClick={() => handleItemClick(item)}
                className="flex items-center gap-2 px-2 py-1 text-sm rounded hover:opacity-80 flex-1 text-left"
                style={{ color: 'var(--color-text)' }}
              >
                <span>{item.isDirectory ? '📁' : '📄'}</span>
                <span className="truncate flex-1">{item.name}</span>
                {!item.isDirectory && (
                  <span className="text-xs" style={{ color: 'var(--color-text-secondary)' }}>
                    {item.size > 1024 ? `${(item.size / 1024).toFixed(1)} KB` : `${item.size} B`}
                  </span>
                )}
              </button>
              {!item.isDirectory && (
                <button
                  onClick={async () => {
                    const path = currentPath === '/' ? `/${item.name}` : `${currentPath}/${item.name}`
                    setPreviewFile(null)
                    setShowVersions(showVersions === path ? null : path)
                  }}
                  className="text-xs px-1.5 py-0.5 rounded opacity-0 group-hover:opacity-100 transition-opacity"
                  style={{ color: 'var(--color-text-secondary)' }}
                  title="版本历史"
                >
                  🕐
                </button>
              )}
            </div>
          ))}
        </div>

        {/* Drag overlay */}
        {dragOver && (
          <div className="absolute inset-0 rounded-lg border-2 border-dashed z-10 flex items-center justify-center"
            style={{
              borderColor: 'var(--color-accent)',
              backgroundColor: 'rgba(37, 99, 235, 0.08)',
              top: 0, left: 0, right: 0, bottom: 0,
            }}
          >
            <div className="text-center">
              <div className="text-2xl mb-2">📁</div>
              <div className="text-sm font-medium" style={{ color: 'var(--color-accent)' }}>
                {uploading ? '上传中…' : '拖放文件到此处上传'}
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Right panel: preview or versions */}
      {(previewFile || showVersions) && (
        <div className="w-1/2 border-l overflow-auto p-4" style={{
          borderColor: 'var(--color-border)',
          backgroundColor: 'var(--color-panel-bg)',
        }}>
          {previewFile && !showVersions && (
            <FilePreview
              path={previewFile.path}
              content={previewFile.content}
              mimeType={previewFile.mimeType}
              onClose={() => setPreviewFile(null)}
            />
          )}
          {showVersions && (
            <VersionPanel
              path={showVersions}
              onRestore={() => { setShowVersions(null); loadFiles(currentPath) }}
              onClose={() => setShowVersions(null)}
            />
          )}
        </div>
      )}
    </div>
  )
}
