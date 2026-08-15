/**
 * 响应式媒体查询 hook — 监听 matchMedia 变化（移动断点 = max-width: 767px，与 index.css 一致）。
 * 关联：IdeShell（抽屉）、ChatView（输入区 padding）。镜像 useTheme.ts 的 matchMedia 用法。
 */
import { useState, useEffect } from 'react'

export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState<boolean>(() =>
    typeof window !== 'undefined' ? window.matchMedia(query).matches : false,
  )

  useEffect(() => {
    const mql = window.matchMedia(query)
    const handler = (e: MediaQueryListEvent) => setMatches(e.matches)
    setMatches(mql.matches)
    mql.addEventListener('change', handler)
    return () => mql.removeEventListener('change', handler)
  }, [query])

  return matches
}

/** 移动断点统一入口（与 index.css @media (max-width: 767px) 对齐） */
export function useIsMobile(): boolean {
  return useMediaQuery('(max-width: 767px)')
}
