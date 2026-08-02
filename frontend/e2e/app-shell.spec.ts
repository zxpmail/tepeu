/**
 * 主壳 / 导航 / 主题 — 真实后端联调
 */
import { test, expect } from '@playwright/test'
import { openIde, openPanel, backToChat } from './helpers/app'

test.describe('主壳与导航', () => {
  test('进入 IDE，侧栏导航各面板可开可回', async ({ page, request }) => {
    await openIde(page, request)

    await expect(page.getByTestId('chat-input')).toBeVisible()
    await expect(page.getByTestId('theme-toggle')).toBeVisible()

    const panels = ['workspace', 'memory', 'skills', 'multi', 'cost', 'terminal', 'provider'] as const
    for (const p of panels) {
      await openPanel(page, p)
      await backToChat(page)
    }
  })

  test('主题切换会改 data-theme 或 class', async ({ page, request }) => {
    await openIde(page, request)
    const toggle = page.getByTestId('theme-toggle')
    const before = await toggle.getAttribute('title')
    await toggle.click()
    const after = await toggle.getAttribute('title')
    expect(after).not.toBe(before)
  })
})
