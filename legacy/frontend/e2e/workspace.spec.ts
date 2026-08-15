/**
 * 工作区 CRUD — 真实 API
 */
import { test, expect } from '@playwright/test'
import { openIde, openPanel, backToChat } from './helpers/app'

test.describe('工作区', () => {
  test('新建工作区并出现在列表', async ({ page, request }) => {
    await openIde(page, request)
    await openPanel(page, 'workspace')

    const name = `e2e-新建-${Date.now()}`
    await page.getByTestId('workspace-new').click()
    await page.getByTestId('workspace-name-input').fill(name)
    await page.getByRole('button', { name: '创建', exact: true }).click()

    await expect(page.getByText(name)).toBeVisible({ timeout: 15_000 })
    await backToChat(page)
  })
})
