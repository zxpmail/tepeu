/**
 * 本机实例令牌 — 保护审批与危险宿主 API。
 * 关联：api/client、useTerminal、SecurityController。
 */

let cached: string | null = null
let loadPromise: Promise<string | null> | null = null

/** 确保已拉取令牌（可重复调用） */
export async function ensureInstanceToken(): Promise<string | null> {
  if (cached) return cached
  if (loadPromise) return loadPromise
  loadPromise = (async () => {
    try {
      const res = await fetch('/api/security/instance-token')
      const body = await res.json()
      if (!res.ok || body.code !== 'OK') return null
      const token = body.data?.enabled ? (body.data.token as string) : null
      cached = token
      if (token) {
        try { localStorage.setItem('tepeu-instance-token', token) } catch { /* ignore */ }
      }
      return token
    } catch {
      try {
        cached = localStorage.getItem('tepeu-instance-token')
      } catch { cached = null }
      return cached
    } finally {
      loadPromise = null
    }
  })()
  return loadPromise
}

export function getInstanceTokenSync(): string | null {
  if (cached) return cached
  try {
    cached = localStorage.getItem('tepeu-instance-token')
  } catch { /* ignore */ }
  return cached
}
