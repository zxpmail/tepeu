/**
 * 记忆面板 — 真实 API 增删查
 */
import { test, expect } from '@playwright/test'
import { openIde, openPanel } from './helpers/app'

test.describe('记忆', () => {
  test('新建记忆并出现在列表', async ({ page, request }) => {
    await openIde(page, request)
    await openPanel(page, 'memory')

    const content = `e2e-记忆-${Date.now()}`
    await page.getByTestId('memory-new').click()
    await page.getByTestId('memory-content-input').fill(content)
    await page.getByTestId('memory-save').click()

    await expect(page.getByText(content)).toBeVisible({ timeout: 15_000 })
  })
})
