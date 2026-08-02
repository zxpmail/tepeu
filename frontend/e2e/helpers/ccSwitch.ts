/**
 * 从本机 CC Switch 读取 DeepSeek 配置（经 scripts/import-cc-switch-deepseek.py）。
 * 不把密钥写入仓库或 Playwright 报告。
 */
import { execFileSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const script = path.resolve(__dirname, '../../../scripts/import-cc-switch-deepseek.py')

export interface DeepSeekConfig {
  apiKey: string
  baseUrl: string
  defaultModel: string
  enabled: boolean
  providerId: string
}

/** 读取 CC Switch DeepSeek；失败返回 null */
export function loadDeepSeekFromCcSwitch(): DeepSeekConfig | null {
  try {
    const out = execFileSync('python', [script, '--emit-json'], {
      encoding: 'utf-8',
      timeout: 15_000,
      windowsHide: true,
    }).trim()
    const cfg = JSON.parse(out) as DeepSeekConfig
    if (!cfg?.apiKey) return null
    return cfg
  } catch {
    return null
  }
}
