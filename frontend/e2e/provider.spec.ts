/**
 * 服务商设置 — 真实保存配置
 */
import { test, expect } from '@playwright/test'
import { openIde, openPanel } from './helpers/app'

test.describe('服务商', () => {
  test('打开服务商面板并可保存密钥', async ({ page, request }) => {
    await openIde(page, request)
    await openPanel(page, 'provider')

    await expect(page.getByRole('heading', { name: 'LLM 服务商' })).toBeVisible()

    const keyInput = page.locator('input[name="tepeu-llm-api-key"]')
    await keyInput.fill('sk-e2e-provider-save-test')
    await page.getByRole('button', { name: '保存', exact: true }).click()

    await expect(page.getByText(/已保存/).first()).toBeVisible({ timeout: 15_000 })
  })

  test('服务商页展示 MCP 状态区块', async ({ page, request }) => {
    await openIde(page, request)
    await openPanel(page, 'provider')
    await expect(page.getByTestId('mcp-status-panel')).toBeVisible()
    await expect(page.getByTestId('mcp-status-panel')).toContainText(/未启用|已启用/)
  })
})


