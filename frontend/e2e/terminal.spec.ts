/**
 * 终端面板 — 真实 WebSocket（不 mock）
 */
import { test, expect } from '@playwright/test'
import { openIde, openPanel } from './helpers/app'

test.describe('终端', () => {
  test('打开终端面板可见 xterm 区域', async ({ page, request }) => {
    await openIde(page, request)
    await openPanel(page, 'terminal')

    // xterm 挂载后会出现 .xterm 容器
    await expect(page.locator('.xterm').first()).toBeVisible({ timeout: 20_000 })
  })
})
