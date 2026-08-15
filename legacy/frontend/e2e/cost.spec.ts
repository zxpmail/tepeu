/**
 * 成本仪表盘 — 预算读写、顶栏告警、硬门禁阻断对话
 */
import { test, expect } from '@playwright/test'
import { openIde, openPanel, backToChat } from './helpers/app'
import { updateBudget } from './helpers/api'

test.describe('成本', () => {
  test('设置预算并保存后展示预算条', async ({ page, request }) => {
    await openIde(page, request)
    await openPanel(page, 'cost')

    await expect(page.getByRole('heading', { name: '成本仪表盘' })).toBeVisible()
    await page.getByTestId('cost-budget-input').fill('12.5')
    await page.getByTestId('cost-save').click()

    await expect(page.getByText(/预算 \$12\.50/)).toBeVisible({ timeout: 15_000 })
  })

  test('零预算软告警：仪表盘与顶栏可见', async ({ page, request }) => {
    const { workspaceId } = await openIde(page, request)
    await updateBudget(request, workspaceId, 0, { hardLimit: false })

    await openPanel(page, 'cost')
    await expect(page.getByTestId('cost-alert-banner')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('cost-alert-banner')).toContainText('零预算')

    await backToChat(page)
    await expect(page.getByTestId('budget-alert-badge')).toBeVisible({ timeout: 15_000 })
  })

  test('零预算硬门禁：阻断新对话并中文提示', async ({ page, request }) => {
    const { workspaceId } = await openIde(page, request)
    await updateBudget(request, workspaceId, 0, { hardLimit: true })

    await openPanel(page, 'cost')
    await expect(page.getByTestId('cost-blocked-banner')).toBeVisible({ timeout: 15_000 })

    await backToChat(page)
    await expect(page.getByTestId('budget-blocked-badge')).toBeVisible({ timeout: 15_000 })

    await page.getByTestId('chat-input').fill('预算门禁探测消息')
    await page.getByTestId('chat-send').click()

    await expect(page.getByTestId('chat-error')).toContainText(/预算已用尽/, { timeout: 20_000 })
  })
})
