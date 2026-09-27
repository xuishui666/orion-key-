"use client"

import { useCallback, useEffect, useRef, useState, type FormEvent } from "react"
import { MessageCircle, Send, X } from "lucide-react"
import { getApiErrorMessage, supportApi, type SupportConversation } from "@/services/api"
import { safeStorageGet, safeStorageRemove, safeStorageSet } from "@/lib/utils"
import { useLocale } from "@/lib/context"

const STORAGE_KEY = "support_conversation"
type Session = { id: string; token: string }

export function SupportWidget() {
  const { locale, t } = useLocale()
  const zh = locale === "zh"
  const [enabled, setEnabled] = useState(false)
  const [open, setOpen] = useState(false)
  const [session, setSession] = useState<Session | null>(null)
  const [conversation, setConversation] = useState<SupportConversation | null>(null)
  const [email, setEmail] = useState("")
  const [orderReference, setOrderReference] = useState("")
  const [text, setText] = useState("")
  const [error, setError] = useState("")
  const [sending, setSending] = useState(false)
  const [unread, setUnread] = useState(0)
  const lastAdminId = useRef<string | null>(null)
  const bottom = useRef<HTMLDivElement>(null)

  useEffect(() => {
    supportApi.config().then(config => setEnabled(config.enabled)).catch(() => {})
    const saved = safeStorageGet("localStorage", STORAGE_KEY)
    if (saved) {
      try {
        const value = JSON.parse(saved) as Session
        if (value.id && value.token) setSession(value)
      } catch { safeStorageRemove("localStorage", STORAGE_KEY) }
    }
  }, [])

  const refresh = useCallback(async () => {
    if (!session || document.visibilityState === "hidden") return
    try {
      const next = await supportApi.get(session.id, session.token)
      setConversation(next)
      const newestAdmin = [...next.messages].reverse().find(m => m.sender === "ADMIN")?.id || null
      if (newestAdmin && lastAdminId.current && newestAdmin !== lastAdminId.current && !open) {
        setUnread(n => n + 1)
      }
      lastAdminId.current = newestAdmin
      setError("")
    } catch (err) {
      setError(getApiErrorMessage(err, t))
    }
  }, [session, open, t])

  useEffect(() => {
    if (!enabled || !session) return
    refresh()
    const interval = window.setInterval(refresh, open ? 8_000 : 30_000)
    return () => window.clearInterval(interval)
  }, [enabled, session, open, refresh])

  useEffect(() => {
    if (open) {
      setUnread(0)
      bottom.current?.scrollIntoView({ behavior: "smooth" })
    }
  }, [open, conversation?.messages.length])

  async function submit(event: FormEvent) {
    event.preventDefault()
    if (!text.trim() || sending) return
    setSending(true)
    setError("")
    try {
      if (session) {
        await supportApi.send(session.id, session.token, text.trim())
        setText("")
        await refresh()
      } else {
        const created = await supportApi.create({ email: email.trim(), order_reference: orderReference.trim(), text: text.trim() })
        safeStorageSet("localStorage", STORAGE_KEY, JSON.stringify(created))
        setSession(created)
        setText("")
        setConversation(await supportApi.get(created.id, created.token))
      }
    } catch (err) {
      setError(getApiErrorMessage(err, t))
    } finally {
      setSending(false)
    }
  }

  if (!enabled) return null

  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(value => !value)}
        aria-label={zh ? "联系客服" : "Contact support"}
        title={zh ? "联系客服" : "Contact support"}
        className="fixed bottom-20 right-4 z-[60] flex h-12 w-12 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-lg"
      >
        {open ? <X className="h-5 w-5" /> : <MessageCircle className="h-5 w-5" />}
        {!open && unread > 0 && <span className="absolute -right-1 -top-1 rounded-full bg-destructive px-1.5 text-xs text-destructive-foreground">{unread}</span>}
      </button>
      {open && (
        <section aria-label={zh ? "客服会话" : "Support chat"} className="fixed bottom-36 left-4 right-4 z-[60] flex h-[min(70dvh,480px)] max-h-[calc(100dvh-10rem)] flex-col overflow-hidden rounded-lg border border-border bg-background shadow-xl sm:left-auto sm:w-[360px]">
          <div className="border-b border-border px-4 py-3 text-sm font-semibold">{zh ? "在线客服" : "Support"}</div>
          <div className="min-h-0 flex-1 space-y-3 overflow-y-auto px-4 py-3" aria-live="polite">
            {conversation?.messages.map(message => (
              <div key={message.id} className={message.sender === "CUSTOMER" ? "flex justify-end" : "flex justify-start"}>
                <p className={`max-w-[85%] whitespace-pre-wrap break-words rounded-lg px-3 py-2 text-sm ${message.sender === "CUSTOMER" ? "bg-primary text-primary-foreground" : "bg-muted text-foreground"}`}>{message.text}</p>
              </div>
            ))}
            {!conversation && <p className="text-sm text-muted-foreground">{zh ? "请描述你的问题，客服收到后会回复。" : "Tell us how we can help."}</p>}
            <div ref={bottom} />
          </div>
          <form onSubmit={submit} className="space-y-2 border-t border-border p-3">
            {!session && (
              <div className="grid grid-cols-2 gap-2">
                <input type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder={zh ? "邮箱（选填）" : "Email (optional)"} className="min-w-0 rounded-md border border-input bg-background px-2 py-2 text-sm" maxLength={254} />
                <input value={orderReference} onChange={e => setOrderReference(e.target.value)} placeholder={zh ? "订单号（选填）" : "Order ID (optional)"} className="min-w-0 rounded-md border border-input bg-background px-2 py-2 text-sm" maxLength={80} />
              </div>
            )}
            <div className="flex items-end gap-2">
              <textarea value={text} onChange={e => setText(e.target.value)} placeholder={zh ? "输入消息" : "Message"} rows={2} maxLength={2000} className="min-w-0 flex-1 resize-none rounded-md border border-input bg-background px-3 py-2 text-sm" />
              <button type="submit" disabled={sending || !text.trim()} title={zh ? "发送" : "Send"} aria-label={zh ? "发送" : "Send"} className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-primary text-primary-foreground disabled:opacity-50"><Send className="h-4 w-4" /></button>
            </div>
            {error && <p role="alert" className="text-xs text-destructive">{error}</p>}
            {session && error && <button type="button" className="text-xs text-primary underline" onClick={() => { safeStorageRemove("localStorage", STORAGE_KEY); setSession(null); setConversation(null); setError("") }}>{zh ? "发起新会话" : "Start a new chat"}</button>}
          </form>
        </section>
      )}
    </>
  )
}

