/**
 * Markdown 渲染 — 对话消息正文。
 * 关联：MessageView；复用 marked + highlight.js。
 */
import { useMemo } from 'react'
import { marked } from 'marked'
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import python from 'highlight.js/lib/languages/python'
import java from 'highlight.js/lib/languages/java'
import bash from 'highlight.js/lib/languages/bash'
import json from 'highlight.js/lib/languages/json'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import markdown from 'highlight.js/lib/languages/markdown'

let langsRegistered = false

/** 注册 highlight.js 语言（只做一次） */
function ensureLangs() {
  if (langsRegistered) return
  hljs.registerLanguage('javascript', javascript)
  hljs.registerLanguage('typescript', typescript)
  hljs.registerLanguage('python', python)
  hljs.registerLanguage('java', java)
  hljs.registerLanguage('bash', bash)
  hljs.registerLanguage('json', json)
  hljs.registerLanguage('xml', xml)
  hljs.registerLanguage('html', xml)
  hljs.registerLanguage('css', css)
  hljs.registerLanguage('markdown', markdown)
  langsRegistered = true
}

marked.setOptions({
  gfm: true,
  breaks: true,
})

interface MarkdownBodyProps {
  content: string
  className?: string
}

/** 将 Markdown 转成安全 HTML（仅信任模型/用户文本，无外链脚本） */
export default function MarkdownBody({ content, className = '' }: MarkdownBodyProps) {
  const html = useMemo(() => {
    ensureLangs()
    const raw = marked.parse(content || '', { async: false }) as string
    // 给表格包一层横向滚动
    const withTables = raw.replace(
      /<table([\s\S]*?)<\/table>/g,
      '<div class="md-table-wrap"><table$1</table></div>',
    )
    // 代码高亮
    return withTables.replace(
      /<pre><code class="language-(\w+)">([\s\S]*?)<\/code><\/pre>/g,
      (_m, lang: string, code: string) => {
        const decoded = code
          .replace(/&lt;/g, '<')
          .replace(/&gt;/g, '>')
          .replace(/&amp;/g, '&')
          .replace(/&quot;/g, '"')
        try {
          const highlighted = hljs.getLanguage(lang)
            ? hljs.highlight(decoded, { language: lang }).value
            : hljs.highlightAuto(decoded).value
          return `<pre><code class="language-${lang} hljs">${highlighted}</code></pre>`
        } catch {
          return `<pre><code class="language-${lang}">${code}</code></pre>`
        }
      },
    )
  }, [content])

  return (
    <div
      className={`markdown-body ${className}`.trim()}
      dangerouslySetInnerHTML={{ __html: html }}
    />
  )
}
