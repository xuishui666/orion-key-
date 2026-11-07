"use client"

import { useCallback, useEffect, useRef, useState, type FormEvent } from "react"
import { RefreshCw, Send, Trash2 } from "lucide-react"
import { adminSupportApi, getApiErrorMessage, type SupportConversation } from "@/services/api"
import { SupportImage } from "@/components/store/support-image"
import { useLocale } from "@/lib/context"
import { Modal } from "@/components/ui/modal"

export default function AdminSupportPage() {
  const { locale, t } = useLocale()
  const zh = locale === "zh"
  const [list, setList] = useState<SupportConversation[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [conversation, setConversation] = useState<SupportConversation | null>(null)
  const [text, setText] = useState("")
  const [sending, setSending] = useState(false)
  const [error, setError] = useState("")
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [deleting, setDeleting] = useState(false)
  const deleted = useRef(new Set<string>())
  const activeSelection = useRef(selectedId)
  activeSelection.current = selectedId

  const refreshList = useCallback(async () => {
    try {
      const result = await adminSupportApi.list()
      const remaining = result.filter(item => !deleted.current.has(item.id))
      setList(remaining)
      setSelectedId(current => remaining.some(item => item.id === current) ? current : remaining[0]?.id || null)
    } catch (err) { setError(getApiErrorMessage(err, t)) }
  }, [t])

  const refreshConversation = useCallback(async () => {
    if (!selectedId) return
    try {
      const result = await adminSupportApi.get(selectedId)
      if (activeSelection.current === selectedId && !deleted.current.has(selectedId)) {
        setConversation(result)
        setError("")
      }
    } catch (err) { if (!deleted.current.has(selectedId)) setError(getApiErrorMessage(err, t)) }
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

  async function removeConversation() {
    if (!deleteId || deleting) return
    const id = deleteId
    setDeleting(true)
    try {
      await adminSupportApi.delete(id)
      deleted.current.add(id)
      activeSelection.current = null
      setConversation(null)
      setSelectedId(null)
      setText("")
      setError("")
      setList(current => current.filter(item => item.id !== id))
      setDeleteId(null)
      await refreshList()
    } catch (err) { setError(getApiErrorMessage(err, t)); setDeleteId(null) }
    finally { setDeleting(false) }
  }

  return (
    <div className="space-y-5">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-semibold">{zh ? "客服会话" : "Support"}</h1>
        <div className="flex gap-2">
          <button type="button" disabled={!selectedId || deleting || sending} onClick={() => setDeleteId(selectedId)} title={zh ? "删除所选会话" : "Delete selected conversation"} aria-label={zh ? "删除所选会话" : "Delete selected conversation"} className="rounded-md border border-border p-2 text-destructive disabled:opacity-40"><Trash2 className="h-4 w-4" /></button>
          <button type="button" onClick={() => { refreshList(); refreshConversation() }} title={zh ? "刷新" : "Refresh"} aria-label={zh ? "刷新" : "Refresh"} className="rounded-md border border-border p-2"><RefreshCw className="h-4 w-4" /></button>
        </div>
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
                    <div className={`max-w-[85%] whitespace-pre-wrap break-words rounded-md px-3 py-2 text-sm ${message.sender === "ADMIN" ? "bg-primary text-primary-foreground" : "bg-muted"}`}>
                      {message.has_image && <SupportImage conversationId={conversation.id} messageId={message.id} />}
                      {message.has_image && message.text !== "[图片]" && <p>{message.text}</p>}
                      {!message.has_image && message.text}
                    </div>
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
      <Modal open={deleteId !== null} onClose={() => { if (!deleting) setDeleteId(null) }} className="max-w-sm rounded-lg p-5">
        <div role="alertdialog" aria-modal="true" aria-labelledby="delete-support-title" aria-describedby="delete-support-description">
          <h2 id="delete-support-title" className="text-base font-semibold">{zh ? "彻底删除此会话？" : "Delete this conversation permanently?"}</h2>
          <p className="mt-2 break-all text-sm text-muted-foreground">{deleteId?.slice(0, 12)}</p>
          <p id="delete-support-description" className="mt-2 text-sm text-muted-foreground">{zh ? "会话内所有消息和图片将被删除，无法恢复。Telegram 中已有消息仍会保留。" : "All messages and images will be permanently deleted. Existing Telegram messages remain."}</p>
          <div className="mt-5 flex justify-end gap-2">
            <button type="button" disabled={deleting} onClick={() => setDeleteId(null)} className="rounded-md border border-border px-3 py-2 text-sm">{zh ? "取消" : "Cancel"}</button>
            <button type="button" disabled={deleting} onClick={removeConversation} className="rounded-md bg-destructive px-3 py-2 text-sm text-destructive-foreground disabled:opacity-50">{deleting ? (zh ? "删除中…" : "Deleting…") : (zh ? "彻底删除" : "Delete permanently")}</button>
          </div>
        </div>
      </Modal>
    </div>
  )
}
