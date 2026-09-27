"use client"

import { useEffect, useState } from "react"
import { adminSupportApi, supportApi } from "@/services/api"

export function SupportImage({ conversationId, messageId, token }: {
  conversationId: string
  messageId: string
  token?: string
}) {
  const [url, setUrl] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    let objectUrl: string | null = null
    const load = token
      ? supportApi.image(conversationId, token, messageId)
      : adminSupportApi.image(conversationId, messageId)
    load.then(blob => {
      if (!active) return
      objectUrl = URL.createObjectURL(blob)
      setUrl(objectUrl)
    }).catch(() => setUrl(null))
    return () => {
      active = false
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [conversationId, messageId, token])

  return url ? <a href={url} target="_blank" rel="noreferrer"><img src={url} alt="Support attachment" className="max-h-52 max-w-full rounded border border-border object-contain" /></a> : null
}
