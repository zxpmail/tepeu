/**
 * 浏览器侧导航助手 — 等 IDE 就绪、切次级面板。
 */
import { expect, type Page } from '@playwright/test'
import type { APIRequestContext } from '@playwright/test'
import { ensureProvider, ensureWorkspace } from './api'

/** 预置后端后打开首页，确认进入 IDE（非 SetupWizard） */
export async function openIde(
  page: Page,
  request: APIRequestContext,
): Promise<{ workspaceId: string; providerId: string }> {
  const providerId = await ensureProvider(request)
  const workspaceId = await ensureWorkspace(request)
  await page.goto('/')
  await expect(page.getByTestId('setup-wizard')).toHaveCount(0, { timeout: 20_000 })
  await expect(page.getByTestId('ide-shell')).toBeVisible({ timeout: 20_000 })
  return { workspaceId, providerId }
}

/** 打开次级面板（侧栏导航） */
export async function openPanel(
  page: Page,
  panel: 'workspace' | 'memory' | 'skills' | 'multi' | 'cost' | 'terminal' | 'provider',
): Promise<void> {
  await page.getByTestId(`nav-${panel}`).click()
  await expect(page.getByRole('button', { name: '← 返回对话' })).toBeVisible()
}

/** 从次级面板回到对话 */
export async function backToChat(page: Page): Promise<void> {
  await page.getByRole('button', { name: '← 返回对话' }).click()
  await expect(page.getByTestId('ide-shell')).toBeVisible()
}
