/**
 * 工作区文件变更事件总线 — 供 useChat / 全局 SSE 发出 file_changed，FileBrowserView 订阅刷新预览。
 * 同时提供 React Context（subscribe / emitFileChanged）。
 * Phase 12：事件可携带 workspaceId；缺省视为「当前工作区」，订阅者按自身工作区过滤。
 */
import { createContext, useContext, useEffect, useMemo, type ReactNode } from 'react'
import { openSharedFileEvents } from '../lib/sharedFileEvents'

export type FileChangedHandler = (path: string, workspaceId?: string) => void

/** 模块级事件总线，供非 React 调用方（如 useChat）直接 emit */
const listeners = new Set<FileChangedHandler>()

export const workspaceEventBus = {
  /** 订阅文件变更；返回取消订阅函数 */
  subscribe(fn: FileChangedHandler): () => void {
    listeners.add(fn)
    return () => { listeners.delete(fn) }
  },
  /** 广播文件路径已变更（workspaceId 缺省 = 当前工作区） */
  emitFileChanged(path: string, workspaceId?: string): void {
    for (const fn of listeners) {
      try { fn(path, workspaceId) } catch { /* 单个订阅者异常不影响其他 */ }
    }
  },
}

interface WorkspaceEventsValue {
  subscribe: (fn: FileChangedHandler) => () => void
  emitFileChanged: (path: string, workspaceId?: string) => void
}

const WorkspaceEventsContext = createContext<WorkspaceEventsValue | null>(null)

/** 提供文件变更订阅/广播能力；同时打开全局文件事件 SSE（GET /api/events）。 */
export function WorkspaceEventsProvider({ children }: { children: ReactNode }) {
  const value = useMemo<WorkspaceEventsValue>(() => ({
    subscribe: workspaceEventBus.subscribe,
    emitFileChanged: workspaceEventBus.emitFileChanged,
  }), [])

  // 常驻事件通道：跨 tab 共享一条 SSE（仅 leader tab 持有连接），推送 file_changed → 模块级总线
  useEffect(() => {
    return openSharedFileEvents((path, workspaceId) => {
      workspaceEventBus.emitFileChanged(path, workspaceId)
    })
  }, [])

  return (
    <WorkspaceEventsContext.Provider value={value}>
      {children}
    </WorkspaceEventsContext.Provider>
  )
}

/** 读取 WorkspaceEvents context；未包裹 Provider 时回退到模块级总线 */
export function useWorkspaceEvents(): WorkspaceEventsValue {
  const ctx = useContext(WorkspaceEventsContext)
  return ctx ?? {
    subscribe: workspaceEventBus.subscribe,
    emitFileChanged: workspaceEventBus.emitFileChanged,
  }
}
