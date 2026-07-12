/**
 * 把消息列表分成：用户消息 / 过程详情组 / 最终回答。
 * 关联：ChatView。
 */
import type { ChatMessage } from '../../hooks/useChat'

export type DisplayItem =
  | { kind: 'message'; message: ChatMessage; key: string }
  | { kind: 'process'; messages: ChatMessage[]; toolCalls: number; key: string }

/** 按 user 轮次折叠中间工具与草稿助手消息 */
export function groupChatMessages(messages: ChatMessage[]): DisplayItem[] {
  const items: DisplayItem[] = []
  let i = 0
  let keySeq = 0
  const nextKey = () => `g-${keySeq++}`

  while (i < messages.length) {
    const m = messages[i]!
    if (m.role === 'user') {
      items.push({ kind: 'message', message: m, key: m.id ?? nextKey() })
      i++
      const batch: ChatMessage[] = []
      while (i < messages.length && messages[i]!.role !== 'user') {
        batch.push(messages[i]!)
        i++
      }
      if (batch.length === 0) continue

      let lastAssistant = -1
      for (let j = batch.length - 1; j >= 0; j--) {
        if (batch[j]!.role === 'assistant') {
          lastAssistant = j
          break
        }
      }

      if (lastAssistant < 0) {
        const toolCalls = batch.filter(b => b.toolKind === 'call').length
        if (batch.length > 0) {
          items.push({ kind: 'process', messages: batch, toolCalls, key: nextKey() })
        }
        continue
      }

      const processMsgs = [...batch.slice(0, lastAssistant), ...batch.slice(lastAssistant + 1)]
      const answer = batch[lastAssistant]!
      const toolCalls = processMsgs.filter(b => b.toolKind === 'call').length
      if (processMsgs.length > 0) {
        items.push({ kind: 'process', messages: processMsgs, toolCalls, key: nextKey() })
      }
      items.push({ kind: 'message', message: answer, key: answer.id ?? nextKey() })
    } else {
      items.push({ kind: 'message', message: m, key: m.id ?? nextKey() })
      i++
    }
  }
  return items
}
