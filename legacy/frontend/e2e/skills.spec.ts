/**
 * 技能面板 — 真实粘贴安装
 */
import { test, expect } from '@playwright/test'
import { openIde, openPanel } from './helpers/app'

test.describe('技能', () => {
  test('粘贴安装技能后列表出现', async ({ page, request }) => {
    await openIde(page, request)
    await openPanel(page, 'skills')

    await expect(page.getByRole('heading', { name: '技能' })).toBeVisible()
    await page.getByRole('button', { name: '改用粘贴 Markdown' }).click()

    const skillName = `e2e-skill-${Date.now()}`
    const markdown = `---\nname: ${skillName}\ndescription: e2e test skill\n---\n\n# ${skillName}\n\nDo nothing.\n`

    await page.getByPlaceholder('粘贴 SKILL.md 全文').fill(markdown)
    await page.getByPlaceholder('名称（可选）').fill(skillName)
    await page.getByRole('button', { name: '安装', exact: true }).click()

    await expect(page.getByText(skillName).first()).toBeVisible({ timeout: 20_000 })
  })
})
