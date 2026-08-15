/**
 * 共享 highlight.js 注册与语言探测。
 * 关联：FileBrowserView、RightFilePanel。
 */
import hljs from 'highlight.js/lib/core'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import json from 'highlight.js/lib/languages/json'
import python from 'highlight.js/lib/languages/python'
import java from 'highlight.js/lib/languages/java'
import bash from 'highlight.js/lib/languages/bash'
import markdown from 'highlight.js/lib/languages/markdown'
import yaml from 'highlight.js/lib/languages/yaml'
import 'highlight.js/styles/github.css'

let registered = false

/** 确保语言包只注册一次 */
export function ensureHljsLanguages(): typeof hljs {
  if (!registered) {
    hljs.registerLanguage('javascript', javascript)
    hljs.registerLanguage('typescript', typescript)
    hljs.registerLanguage('xml', xml)
    hljs.registerLanguage('css', css)
    hljs.registerLanguage('json', json)
    hljs.registerLanguage('python', python)
    hljs.registerLanguage('java', java)
    hljs.registerLanguage('bash', bash)
    hljs.registerLanguage('markdown', markdown)
    hljs.registerLanguage('yaml', yaml)
    registered = true
  }
  return hljs
}

/** 按文件名扩展名推断高亮语言 */
export function detectLanguage(filename: string): string {
  const ext = filename.split('.').pop()?.toLowerCase() || ''
  const map: Record<string, string> = {
    ts: 'typescript', tsx: 'typescript', js: 'javascript', jsx: 'javascript',
    html: 'xml', htm: 'xml', svg: 'xml', css: 'css', scss: 'css',
    json: 'json', py: 'python', java: 'java',
    md: 'markdown', mkd: 'markdown',
    sh: 'bash', bash: 'bash', zsh: 'bash', cmd: 'bash', bat: 'bash',
    yml: 'yaml', yaml: 'yaml',
  }
  return map[ext] || ''
}

/** 高亮整段代码，失败则原样返回 */
export function highlightCode(code: string, filename: string): string {
  const engine = ensureHljsLanguages()
  const lang = detectLanguage(filename)
  try {
    if (lang && engine.getLanguage(lang)) {
      return engine.highlight(code, { language: lang }).value
    }
    return engine.highlightAuto(code).value
  } catch {
    return code
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
  }
}
