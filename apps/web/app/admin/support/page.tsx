"use client"

import { useCallback, useEffect, useState, type FormEvent } from "react"
import { RefreshCw, Send } from "lucide-react"
import { adminSupportApi, getApiErrorMessage, type SupportConversation } from "@/services/api"
import { useLocale } from "@/lib/context"

export default function AdminSupportPage() {
  const { locale, t } = useLocale()
  const zh = locale === "zh"
  const [list, setList] = useState<SupportConversation[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [conversation, setConversation] = useState<SupportConversation | null>(null)
  const [text, setText] = useState("")
  const [sending, setSending] = useState(false)
  const [error, setError] = useState("")

  const refreshList = useCallback(async () => {
    try {
      const result = await adminSupportApi.list()
      setList(result)
      setSelectedId(current => current || result[0]?.id || null)
    } catch (err) { setError(getApiErrorMessage(err, t)) }
  }, [t])

  const refreshConversation = useCallback(async () => {
    if (!selectedId) return
    try {
      setConversation(await adminSupportApi.get(selectedId))
      setError("")
    } catch (err) { setError(getApiErrorMessage(err, t)) }
  }, [selectedId, t])

  useEffect(() => { refreshList() }, [refreshList])
  useEffect(() => {
    refreshConversation()
    const interval = window.setInterval(refreshConversation, 15_000)
    return () => window.clearInterval(interval)
  }, [refreshConversation])

  async function send(event: FormEvent) {
    event.preventDefault()
    if (!selectedId || !text.trim() || sending) return
    setSending(true)
    try {
      await adminSupportApi.send(selectedId, text.trim())
      setText("")
      await refreshConversation()
      await refreshList()
    } catch (err) { setError(getApiErrorMessage(err, t)) }
    finally { setSending(false) }
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">{zh ? "客服会话" : "Support"}</h1>
        <button type="button" onClick={() => { refreshList(); refreshConversation() }} title={zh ? "刷新" : "Refresh"} aria-label={zh ? "刷新" : "Refresh"} className="rounded-md border border-border p-2"><RefreshCw className="h-4 w-4" /></button>
      </div>
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      <div className="grid gap-4 md:grid-cols-[260px_minmax(0,1fr)]">
        <div className="max-h-48 overflow-y-auto border border-border md:max-h-[65vh]">
          {list.length === 0 && <p className="p-4 text-sm text-muted-foreground">{zh ? "暂无会话" : "No conversations"}</p>}
          {list.map(item => (
            <button key={item.id} type="button" onClick={() => { setSelectedId(item.id); setConversation(null) }} className={`block w-full border-b border-border p-3 text-left text-sm last:border-0 ${item.id === selectedId ? "bg-primary/10" : "hover:bg-muted"}`}>
              <span className="block truncate font-medium">{item.order_reference || item.email || item.id.slice(0, 8)}</span>
              <span className="text-xs text-muted-foreground">{new Date(item.last_activity_at).toLocaleString()}</span>
            </button>
          ))}
        </div>
        <div className="flex min-h-80 flex-col border border-border">
          {conversation ? (
            <>
              <div className="border-b border-border px-4 py-3 text-sm">
                <span className="font-medium">{conversation.order_reference || (zh ? "普通咨询" : "General enquiry")}</span>
                {conversation.email && <span className="ml-2 text-muted-foreground">{conversation.email}</span>}
              </div>
              <div className="max-h-[55vh] min-h-60 flex-1 space-y-3 overflow-y-auto p-4">
                {conversation.messages.map(message => (
                  <div key={message.id} className={message.sender === "ADMIN" ? "flex justify-end" : "flex justify-start"}>
                    <p className={`max-w-[85%] whitespace-pre-wrap break-words rounded-md px-3 py-2 text-sm ${message.sender === "ADMIN" ? "bg-primary text-primary-foreground" : "bg-muted"}`}>{message.text}</p>
                  </div>
                ))}
              </div>
              <form onSubmit={send} className="flex gap-2 border-t border-border p-3">
                <input value={text} onChange={event => setText(event.target.value)} maxLength={2000} placeholder={zh ? "回复客户" : "Reply"} className="min-w-0 flex-1 rounded-md border border-input bg-background px-3 py-2 text-sm" />
                <button type="submit" disabled={sending || !text.trim()} title={zh ? "发送" : "Send"} aria-label={zh ? "发送" : "Send"} className="rounded-md bg-primary px-3 text-primary-foreground disabled:opacity-50"><Send className="h-4 w-4" /></button>
              </form>
            </>
          ) : <p className="p-4 text-sm text-muted-foreground">{zh ? "选择会话查看消息" : "Select a conversation"}</p>}
        </div>
      </div>
    </div>
  )
}

