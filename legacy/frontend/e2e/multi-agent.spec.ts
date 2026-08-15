/**
 * 多 Agent 面板 — UI + 真实启动请求（不要求 LLM 成功）
 */
import { test, expect } from '@playwright/test'
import { openIde, openPanel } from './helpers/app'

test.describe('多 Agent', () => {
  test('填写目标后可点开始，页面进入运行或报错（真实后端）', async ({ page, request }) => {
    await openIde(page, request)
    await openPanel(page, 'multi')

    await expect(page.getByRole('heading', { name: '多 Agent 协作' })).toBeVisible()
    await page.getByTestId('multi-goal-input').fill('e2e: 只验证流水线入口')
    const start = page.getByTestId('multi-start')
    await expect(start).toBeEnabled()
    await start.click()

    // 运行中… 或错误信息（假密钥会导致后端失败）— 都证明未 mock
    await expect(
      page.getByText(/运行中|Planner|失败|error|Error|Connection|API|密钥|未配置/i).first(),
    ).toBeVisible({ timeout: 30_000 })
  })
})
