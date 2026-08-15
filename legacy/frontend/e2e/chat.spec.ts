/**
 * 对话 — 真实 SSE；有 CC Switch DeepSeek 时可能成功回复，否则期望错误/停止。
 */
import { test, expect } from '@playwright/test'
import { openIde } from './helpers/app'

test.describe('对话', () => {
  test('输入并发送会打真实后端（用户消息 + 回复或错误或停止）', async ({ page, request }) => {
    await openIde(page, request)

    const text = `e2e-chat-${Date.now()}`
    await page.getByTestId('chat-input').fill(text)
    await page.getByTestId('chat-send').click()

    await expect(page.getByText(text).first()).toBeVisible({ timeout: 15_000 })

    const error = page.getByTestId('chat-error')
    const stop = page.getByRole('button', { name: '停止' })
    // 真实 DeepSeek 成功时用户消息之外还会出现助手内容；失败则 error；流式则停止
    await expect(error.or(stop).or(page.getByText(text).nth(1))).toBeVisible({ timeout: 90_000 })
  })

  test('斜杠命令菜单真实打开', async ({ page, request }) => {
    await openIde(page, request)
    await page.getByTestId('chat-input').fill('/')
    await expect(page.getByText('清空对话')).toBeVisible({ timeout: 10_000 })
  })

  test('/help 不走模型直接出结果', async ({ page, request }) => {
    await openIde(page, request)
    await page.getByTestId('chat-input').fill('/help')
    await page.getByTestId('chat-send').click()
    await expect(page.getByText(/内置命令/).first()).toBeVisible({ timeout: 15_000 })
    await expect(page.getByText('/schedule').first()).toBeVisible()
  })

  test('手打 /clear 清空对话不送模型', async ({ page, request }) => {
    await openIde(page, request)
    // 先用 /help 插入一轮，再 /clear
    await page.getByTestId('chat-input').fill('/help')
    await page.getByTestId('chat-send').click()
    await expect(page.getByText(/内置命令/).first()).toBeVisible({ timeout: 15_000 })
    await page.getByTestId('chat-input').fill('/clear')
    await page.getByTestId('chat-send').click()
    await expect(page.getByText(/内置命令/)).toHaveCount(0, { timeout: 10_000 })
    await expect(page.getByTestId('chat-input')).toHaveValue('')
    await expect(page.getByText('/clear')).toHaveCount(0)
  })
})
