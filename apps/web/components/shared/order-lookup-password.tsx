"use client"

import { useLocale } from "@/lib/context"

export function OrderLookupPassword({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  const { locale } = useLocale()
  const zh = locale === "zh"
  return (
    <div className="mt-4 space-y-1.5">
      <label htmlFor="order-lookup-password" className="block text-sm font-medium">{zh ? "查询密码" : "Order lookup password"}</label>
      <input id="order-lookup-password" type="password" autoComplete="new-password" minLength={8} maxLength={72}
        value={value} onChange={event => onChange(event.target.value)}
        className="h-10 w-full rounded-md border border-input bg-background px-3 text-sm" />
      <p className="text-xs text-muted-foreground">{zh ? "至少 8 位。跨设备查单时需提供，请妥善保管。" : "At least 8 characters. Keep it for order lookup on another device."}</p>
    </div>
  )
}
