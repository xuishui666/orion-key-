"use client"

import { useEffect, useRef, useCallback, useState } from "react"
import { useSiteConfig, useTheme } from "@/lib/context"

const TURNSTILE_SCRIPT_URL = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit"

interface TurnstileProps {
  /** 获取 token 后的回调 */
  onSuccess: (token: string) => void
  /** 验证失败/过期的回调 */
  onError?: () => void
  /** 组件 className */
  className?: string
}

let scriptPromise: Promise<void> | null = null

function loadScript(): Promise<void> {
  if (scriptPromise) return scriptPromise
  scriptPromise = new Promise<void>((resolve, reject) => {
    const script = document.createElement("script")
    const timer = window.setTimeout(() => {
      script.remove()
      reject(new Error("Turnstile load timed out"))
    }, 15000)
    script.src = TURNSTILE_SCRIPT_URL
    script.async = true
    script.onload = () => { window.clearTimeout(timer); resolve() }
    script.onerror = () => {
      window.clearTimeout(timer)
      script.remove()
      reject(new Error("Turnstile script failed"))
    }
    document.head.appendChild(script)
  }).catch(error => { scriptPromise = null; throw error })
  return scriptPromise
}

/**
 * Cloudflare Turnstile 组件（Managed 模式） *
 * 正常用户完全无感，可疑时自动出现复选框。 * token 单次有效，提交失败后调用 reset() 获取新 token。 */
export function Turnstile({ onSuccess, onError, className }: TurnstileProps) {
  const [failed, setFailed] = useState(false)
  const [attempt, setAttempt] = useState(0)
  const containerRef = useRef<HTMLDivElement>(null)
  const widgetIdRef = useRef<string | null>(null)
  const { config } = useSiteConfig()
  const { resolvedTheme } = useTheme()
  // 从后端 /api/site/config 获取 site key（运行时读取，不依赖构建时环境变量）
  const siteKey = config ? (config as unknown as Record<string, unknown>).turnstile_site_key as string | undefined : undefined

  useEffect(() => {
    if (!siteKey || !containerRef.current) return

    let mounted = true

    loadScript().then(() => {
      if (!mounted || !containerRef.current) return

      const turnstile = (window as unknown as Record<string, unknown>).turnstile as {
        render: (container: HTMLElement, options: Record<string, unknown>) => string
        reset: (widgetId: string) => void
        remove: (widgetId: string) => void
      } | undefined

      if (!turnstile) {
        setFailed(true)
        onError?.()
        return
      }

      // 防止重复渲染
      if (widgetIdRef.current) {
        try { turnstile.remove(widgetIdRef.current) } catch { /* ignore */ }
      }

      widgetIdRef.current = turnstile.render(containerRef.current!, {
        sitekey: siteKey,
        callback: (token: string) => onSuccess(token),
        "error-callback": () => onError?.(),
        "expired-callback": () => onError?.(),
        theme: resolvedTheme === "dark" ? "dark" : "light",
        size: "flexible",
      })
    }).catch(() => {
      if (mounted) {
        setFailed(true)
        onError?.()
      }
    })

    return () => {
      mounted = false
      if (widgetIdRef.current) {
        try {
          const turnstile = (window as unknown as Record<string, unknown>).turnstile as {
            remove: (widgetId: string) => void
          } | undefined
          turnstile?.remove(widgetIdRef.current)
        } catch { /* ignore */ }
        widgetIdRef.current = null
      }
    }
  }, [siteKey, resolvedTheme, onSuccess, onError, attempt])

  if (!siteKey) return null

  return <div className={className}>
    <div ref={containerRef} />
    {failed && <button type="button" className="text-sm text-destructive underline" onClick={() => {
      setFailed(false)
      setAttempt(value => value + 1)
    }}>验证加载失败，点击重试</button>}
  </div>
}

/** 重置 Turnstile 组件获取新 token（提交失败后调用） */
export function resetTurnstile() {
  const turnstile = (window as unknown as Record<string, unknown>).turnstile as {
    reset: (widgetId?: string) => void
  } | undefined
  turnstile?.reset()
}

export function useTurnstile() {
  const [turnstileToken, setTurnstileToken] = useState<string>("")
  const { config } = useSiteConfig()
  const siteKey = config ? (config as unknown as Record<string, unknown>).turnstile_site_key as string | undefined : undefined
  const turnstileRequired = !!siteKey
  const turnstileReady = config != null && (!turnstileRequired || !!turnstileToken)

  const handleTurnstileReset = useCallback(() => {
    setTurnstileToken("")
    resetTurnstile()
  }, [])

  return { turnstileToken, setTurnstileToken, turnstileRequired, turnstileReady, handleTurnstileReset }
}
