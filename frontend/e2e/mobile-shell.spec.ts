/**
 * 移动端冒烟（375×667）— 抽屉导航 / 发消息 / Slash 候选 / 文件预览（Phase 15 验收 + Phase 14×15 交面）。
 * 桌面回归由既有 specs（1280×800）覆盖；本 spec 用 test.use 声明移动 viewport。
 * 文件在 openIde 之前写入，避免依赖文件树刷新事件（SSE/REST 时序脆弱）。
 */
import { test, expect } from '@playwright/test'
import { ensureWorkspace, writeFile } from './helpers/api'
import { openIde } from './helpers/app'

test.use({ viewport: { width: 375, height: 667 } })

test.describe('移动端（375×667）', () => {
  test('开工作区入口、发一条消息、开 Slash 候选、打开文件预览', async ({ page, request }) => {
    const workspaceId = await ensureWorkspace(request)
    const fileName = `mobile-note-${Date.now()}.md`
    await writeFile(request, `/${fileName}`, '# mobile e2e\n', workspaceId)
    await openIde(page, request)

    // 1) 开抽屉 → 工作区入口
    await page.getByTestId('sidebar-toggle').click()
    await expect(page.getByTestId('drawer-backdrop')).toBeVisible()
    await page.getByTestId('nav-workspace').click()
    await expect(page.getByRole('button', { name: '← 返回对话' })).toBeVisible()
    await page.getByRole('button', { name: '← 返回对话' }).click()
    await expect(page.getByTestId('ide-shell')).toBeVisible()

    // 2) 发一条消息（真实后端；成功/错误/停止皆可）
    const text = `e2e-mobile-${Date.now()}`
    await page.getByTestId('chat-input').fill(text)
    await page.getByTestId('chat-send').click()
    await expect(page.getByText(text).first()).toBeVisible({ timeout: 15_000 })
    const error = page.getByTestId('chat-error')
    const stop = page.getByRole('button', { name: '停止' })
    await expect(error.or(stop).or(page.getByText(text).nth(1))).toBeVisible({ timeout: 90_000 })

    // 3) Slash 候选在 375px 可弹出（UI 命令静态可见，不依赖后端）
    await page.getByTestId('chat-input').fill('/')
    await expect(page.getByText('清空对话').first()).toBeVisible({ timeout: 10_000 })
    await page.getByTestId('chat-input').fill('')

    // 4) 开抽屉 → 点文件 → 全屏预览
    await page.getByTestId('sidebar-toggle').click()
    await page.getByRole('button', { name: new RegExp(fileName) }).click()
    await expect(page.getByTestId('file-preview')).toBeVisible({ timeout: 15_000 })
    await expect(page.getByTestId('file-preview').getByText(fileName)).toBeVisible({ timeout: 15_000 })

    // 5) 预览 ✕ 真正关闭面板（Phase 15 修复：不再只清 openFile 留空面板）
    await page.getByRole('button', { name: '关闭' }).click()
    await expect(page.getByTestId('file-preview')).toBeHidden({ timeout: 10_000 })

    // 6) 移动端点「+ 新建」后抽屉收起（Phase 15 修复：与打开文件收起一致）
    await page.getByTestId('sidebar-toggle').click()
    await expect(page.getByTestId('drawer-backdrop')).toBeVisible()
    await page.getByTestId('btn-new-session').click()
    await expect(page.getByTestId('drawer-backdrop')).toBeHidden()
  })
})
