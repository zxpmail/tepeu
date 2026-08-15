/**
 * 真实 LLM 冒烟 — 使用 CC Switch DeepSeek 密钥（无密钥则跳过）。
 * 不 mock；会产生少量 Token 费用。
 */
import { test, expect } from '@playwright/test'
import { loadDeepSeekFromCcSwitch } from './helpers/ccSwitch'
import { openIde } from './helpers/app'

const hasDeepSeek = !!loadDeepSeekFromCcSwitch()

test.describe('对话 · 真实 DeepSeek', () => {
  test.skip(!hasDeepSeek, '本机 CC Switch 无 DeepSeek 密钥，跳过')

  test('发送短问题并收到助手回复', async ({ page, request }) => {
    const { providerId } = await openIde(page, request)
    expect(providerId).toBe('deepseek')

    // 底栏服务商选 DeepSeek（若默认已是则无妨）
    const providerSelect = page.locator('select.chat-select').first()
    if (await providerSelect.count()) {
      await providerSelect.selectOption('deepseek').catch(() => {})
    }

    const marker = `E2E${Date.now().toString().slice(-6)}`
    await page.getByTestId('chat-input').fill(
      `请只回复一个词：${marker}。不要解释，不要标点以外的内容。`,
    )
    await page.getByTestId('chat-send').click()

    await expect(page.getByTestId('chat-error')).toHaveCount(0, { timeout: 5_000 }).catch(() => {})

    // 助手气泡出现 marker（或至少出现非错误的助手内容）
    await expect(page.getByText(marker).nth(1)).toBeVisible({ timeout: 90_000 })
  })
})
