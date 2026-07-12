/**
 * 工作区文件变更事件总线 — 供 useChat 发出 file_changed，FileBrowserView 订阅刷新预览。
 * 同时提供 React Context（subscribe / emitFileChanged）。
 */
import { createContext, useContext, useMemo, type ReactNode } from 'react'

export type FileChangedHandler = (path: string) => void

/** 模块级事件总线，供非 React 调用方（如 useChat）直接 emit */
const listeners = new Set<FileChangedHandler>()

export const workspaceEventBus = {
  /** 订阅文件变更；返回取消订阅函数 */
  subscribe(fn: FileChangedHandler): () => void {
    listeners.add(fn)
    return () => { listeners.delete(fn) }
  },
  /** 广播文件路径已变更 */
  emitFileChanged(path: string): void {
    for (const fn of listeners) {
      try { fn(path) } catch { /* 单个订阅者异常不影响其他 */ }
    }
  },
}

interface WorkspaceEventsValue {
  subscribe: (fn: FileChangedHandler) => () => void
  emitFileChanged: (path: string) => void
}

const WorkspaceEventsContext = createContext<WorkspaceEventsValue | null>(null)

/** 提供文件变更订阅/广播能力 */
export function WorkspaceEventsProvider({ children }: { children: ReactNode }) {
  const value = useMemo<WorkspaceEventsValue>(() => ({
    subscribe: workspaceEventBus.subscribe,
    emitFileChanged: workspaceEventBus.emitFileChanged,
  }), [])

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
