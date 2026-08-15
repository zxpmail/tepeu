/**
 * 文件树 — 后端写文件后前端列表可见
 */
import { test, expect } from '@playwright/test'
import { writeFile } from './helpers/api'
import { openIde } from './helpers/app'

test.describe('文件', () => {
  test('后端写入文件后侧栏文件树可见', async ({ page, request }) => {
    const { workspaceId } = await openIde(page, request)
    const fileName = `e2e-note-${Date.now()}.md`
    await writeFile(request, `/${fileName}`, '# hello e2e\n', workspaceId)

    const refresh = page.getByTitle('刷新文件列表')
    await refresh.click()
    await expect(page.getByRole('button', { name: new RegExp(fileName) })).toBeVisible({
      timeout: 15_000,
    })
  })
})
