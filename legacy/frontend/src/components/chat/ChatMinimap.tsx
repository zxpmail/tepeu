/**
 * 对话小地图 — 右侧竖条标记用户/助手消息位置，可拖拽跳转。
 * 关联：ChatView；参考 pi-web ChatMinimap。
 */
import {
  useEffect, useRef, useState, useCallback, useMemo,
  type RefObject, type MouseEvent as ReactMouseEvent,
} from 'react'
import type { ChatMessage } from '../../hooks/useChat'
import type { DisplayItem } from './groupMessages'

const MINIMAP_WIDTH = 36

export interface MinimapNodeSource {
  key: string
  role: 'user' | 'assistant' | 'process'
  preview: string
}

/** 从展示项提取小地图节点（与 ChatView 挂 ref 的项一一对应） */
export function toMinimapSources(items: DisplayItem[]): MinimapNodeSource[] {
  const out: MinimapNodeSource[] = []
  for (const item of items) {
    if (item.kind === 'process') {
      out.push({
        key: item.key,
        role: 'process',
        preview: `过程 · ${item.toolCalls} 次工具`,
      })
      continue
    }
    const m = item.message
    const role = m.role === 'user' ? 'user' : 'assistant'
    out.push({
      key: item.key,
      role,
      preview: previewOf(m),
    })
  }
  return out
}

function previewOf(m: ChatMessage): string {
  const t = (m.content || '').replace(/\s+/g, ' ').trim()
  return t.slice(0, 120) || (m.streaming ? '…' : '')
}

function nodeColor(role: MinimapNodeSource['role']): { bg: string; border: string } {
  if (role === 'user') {
    return { bg: 'rgba(37,99,235,0.18)', border: 'rgba(37,99,235,0.7)' }
  }
  if (role === 'process') {
    return { bg: 'rgba(34,197,94,0.15)', border: 'rgba(34,197,94,0.55)' }
  }
  return { bg: 'rgba(107,114,128,0.12)', border: 'rgba(107,114,128,0.5)' }
}

interface NodeInfo {
  topRatio: number
  source: MinimapNodeSource
  index: number
}

interface ChatMinimapProps {
  sources: MinimapNodeSource[]
  scrollContainer: RefObject<HTMLDivElement | null>
  /** 与 sources 一一对应的 DOM 节点 */
  itemRefs: RefObject<(HTMLDivElement | null)[]>
}

export default function ChatMinimap({ sources, scrollContainer, itemRefs }: ChatMinimapProps) {
  const [scrollRatio, setScrollRatio] = useState(0)
  const [viewportRatio, setViewportRatio] = useState(1)
  const [visible, setVisible] = useState(false)
  const [nodes, setNodes] = useState<NodeInfo[]>([])
  const [hovered, setHovered] = useState(false)
  const [mouseYRatio, setMouseYRatio] = useState<number | null>(null)
  const draggingRef = useRef(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const sourcesRef = useRef(sources)
  sourcesRef.current = sources

  const updatePositionsRef = useRef<() => void>(() => {})
  updatePositionsRef.current = () => {
    const scrollEl = scrollContainer.current
    if (!scrollEl) return

    const totalH = scrollEl.scrollHeight
    const clientH = scrollEl.clientHeight
    const scrollable = totalH - clientH

    setVisible(scrollable > 40)
    if (scrollable <= 0) {
      setScrollRatio(0)
      setViewportRatio(1)
    } else {
      setScrollRatio(scrollEl.scrollTop / scrollable)
      setViewportRatio(clientH / totalH)
    }

    const refs = itemRefs.current
    const list = sourcesRef.current
    const next: NodeInfo[] = []
    for (let i = 0; i < list.length; i++) {
      const el = refs?.[i]
      const src = list[i]
      if (!el || !src || totalH <= 0) continue
      const elRect = el.getBoundingClientRect()
      const containerRect = scrollEl.getBoundingClientRect()
      const top = elRect.top - containerRect.top + scrollEl.scrollTop
      next.push({
        topRatio: Math.max(0, Math.min(1, top / totalH)),
        source: src,
        index: next.length,
      })
    }
    setNodes(next)
  }

  const updatePositions = useCallback(() => updatePositionsRef.current(), [])

  useEffect(() => {
    const el = scrollContainer.current
    if (!el) return
    el.addEventListener('scroll', updatePositions, { passive: true })
    const ro = new ResizeObserver(updatePositions)
    ro.observe(el)
    if (el.firstElementChild) ro.observe(el.firstElementChild)
    updatePositions()
    return () => {
      el.removeEventListener('scroll', updatePositions)
      ro.disconnect()
    }
  }, [scrollContainer, updatePositions])

  useEffect(() => {
    const t = setTimeout(updatePositions, 50)
    return () => clearTimeout(t)
  }, [sources.length, updatePositions])

  const scrollToMinimapRatio = useCallback((viewportTopRatio: number) => {
    const el = scrollContainer.current
    if (!el) return
    const scrollable = el.scrollHeight - el.clientHeight
    if (scrollable <= 0) return
    const denom = 1 - viewportRatio
    const clamped = Math.max(0, Math.min(denom > 0 ? denom : 1, viewportTopRatio))
    el.scrollTop = denom > 0 ? (clamped / denom) * scrollable : 0
  }, [scrollContainer, viewportRatio])

  const handleMouseDown = useCallback((e: ReactMouseEvent<HTMLDivElement>) => {
    if (!visible) return
    draggingRef.current = true
    const rect = e.currentTarget.getBoundingClientRect()
    const clickRatio = (e.clientY - rect.top) / rect.height
    const grabOffset = clickRatio - scrollRatio * (1 - viewportRatio)
    const insideBox = grabOffset >= 0 && grabOffset <= viewportRatio
    const offset = insideBox ? grabOffset : viewportRatio / 2
    scrollToMinimapRatio(clickRatio - offset)

    const onMove = (ev: MouseEvent) => {
      if (!draggingRef.current) return
      const r = (ev.clientY - rect.top) / rect.height
      scrollToMinimapRatio(r - offset)
    }
    const onUp = () => {
      draggingRef.current = false
      window.removeEventListener('mousemove', onMove)
      window.removeEventListener('mouseup', onUp)
    }
    window.addEventListener('mousemove', onMove)
    window.addEventListener('mouseup', onUp)
  }, [visible, viewportRatio, scrollRatio, scrollToMinimapRatio])

  const TOOLTIP_HEIGHT = 22
  const TOOLTIP_GAP = 2
  const minimapHeightPx = containerRef.current?.clientHeight ?? 600

  const tooltipPositions = useMemo(() => {
    if (!hovered || nodes.length === 0) return []
    const positions = nodes.map(node =>
      Math.round(node.topRatio * minimapHeightPx - TOOLTIP_HEIGHT / 2),
    )
    for (let pass = 0; pass < 10; pass++) {
      for (let i = 1; i < positions.length; i++) {
        const minTop = positions[i - 1]! + TOOLTIP_HEIGHT + TOOLTIP_GAP
        if (positions[i]! < minTop) positions[i] = minTop
      }
      for (let i = positions.length - 2; i >= 0; i--) {
        const maxTop = positions[i + 1]! - TOOLTIP_HEIGHT - TOOLTIP_GAP
        if (positions[i]! > maxTop) positions[i] = maxTop
      }
    }
    for (let i = 0; i < positions.length; i++) {
      positions[i] = Math.max(0, Math.min(minimapHeightPx - TOOLTIP_HEIGHT, positions[i]!))
    }
    return positions
  }, [hovered, nodes, minimapHeightPx])

  if (!visible) return null

  const viewportBoxTop = scrollRatio * (1 - viewportRatio) * 100
  const viewportBoxHeight = viewportRatio * 100

  const nearestIndex = mouseYRatio !== null && nodes.length > 0
    ? nodes.reduce((best, node) =>
        Math.abs(node.topRatio - mouseYRatio) < Math.abs(nodes[best]!.topRatio - mouseYRatio)
          ? node.index
          : best, 0)
    : null

  return (
    <div
      ref={containerRef}
      className="chat-minimap hidden md:block"
      onMouseDown={handleMouseDown}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => { setHovered(false); setMouseYRatio(null) }}
      onMouseMove={(e) => {
        const rect = e.currentTarget.getBoundingClientRect()
        setMouseYRatio((e.clientY - rect.top) / rect.height)
      }}
      style={{
        width: MINIMAP_WIDTH,
        flexShrink: 0,
        position: 'relative',
        cursor: 'default',
        userSelect: 'none',
        borderLeft: '1px solid var(--color-border)',
        background: 'var(--color-bg-secondary)',
        overflow: 'visible',
      }}
    >
      <div
        style={{
          position: 'absolute',
          left: 0,
          right: 0,
          top: `${viewportBoxTop}%`,
          height: `${viewportBoxHeight}%`,
          background: 'rgba(100,100,100,0.1)',
          borderTop: '1px solid rgba(100,100,100,0.2)',
          borderBottom: '1px solid rgba(100,100,100,0.2)',
          pointerEvents: 'none',
          zIndex: 1,
        }}
      />

      <div
        style={{
          position: 'absolute',
          left: '50%',
          top: 0,
          bottom: 0,
          width: 1,
          background: 'var(--color-border)',
          transform: 'translateX(-50%)',
          zIndex: 0,
        }}
      />

      {nodes.map((node) => {
        const color = nodeColor(node.source.role)
        const isNearest = hovered && nearestIndex === node.index
        const isUser = node.source.role === 'user'
        return (
          <div
            key={node.source.key}
            style={{
              position: 'absolute',
              top: `${node.topRatio * 100}%`,
              transform: 'translateY(-50%)',
              left: 0,
              right: 0,
              height: 12,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              cursor: 'pointer',
              zIndex: 2,
            }}
          >
            <div
              style={{
                width: isUser ? 8 : 6,
                height: isUser ? 8 : 6,
                borderRadius: isUser ? 2 : '50%',
                background: color.bg,
                border: `1.5px solid ${color.border}`,
                flexShrink: 0,
                transition: 'transform 0.1s',
                transform: isNearest ? 'scale(1.6)' : 'scale(1)',
              }}
            />
          </div>
        )
      })}

      {hovered && nodes.map((node, i) => {
        const preview = node.source.preview
        const color = nodeColor(node.source.role)
        const isNearest = nearestIndex === node.index
        if (!preview || tooltipPositions.length === 0) return null
        return (
          <div
            key={`tip-${node.source.key}`}
            style={{
              position: 'absolute',
              top: tooltipPositions[i],
              right: '100%',
              marginRight: 6,
              background: 'var(--color-bg)',
              border: `1px solid ${isNearest ? color.border : 'var(--color-border)'}`,
              borderLeft: `2px solid ${color.border}`,
              borderRadius: 4,
              padding: '2px 7px',
              width: 200,
              zIndex: 100,
              pointerEvents: 'none',
              opacity: isNearest ? 1 : 0.45,
              transition: 'top 0.1s, opacity 0.1s',
            }}
          >
            <div
              style={{
                fontSize: 11,
                color: isNearest ? 'var(--color-text)' : 'var(--color-text-dim)',
                lineHeight: 1.4,
                whiteSpace: 'nowrap',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
              }}
            >
              {preview}
            </div>
          </div>
        )
      })}
    </div>
  )
}

/** 稳定的消息 DOM refs 数组 */
export function useItemRefs(count: number): RefObject<(HTMLDivElement | null)[]> {
  const refs = useRef<(HTMLDivElement | null)[]>([])
  // 保持数组长度与节点数一致
  if (refs.current.length !== count) {
    refs.current = Array.from({ length: count }, (_, i) => refs.current[i] ?? null)
  }
  return refs
}
