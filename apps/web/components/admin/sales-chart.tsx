"use client"

import { useState } from "react"
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from "recharts"
import { useLocale } from "@/lib/context"
import type { SalesTrend } from "@/types"

export function SalesChart({ trends }: { trends: SalesTrend[] }) {
  const { t } = useLocale()
  const [metric, setMetric] = useState<"sales_amount" | "order_count">("sales_amount")
  const sales = metric === "sales_amount"
  const label = t(sales ? "admin.salesAmount" : "admin.orderCount")
  const total = trends.reduce((sum, day) => sum + Number(day[metric] || 0), 0)
  const format = (value: number) => sales ? `¥${value.toLocaleString(undefined, { maximumFractionDigits: 2 })}` : value.toLocaleString()

  return (
    <section className="min-w-0 border-t border-border bg-card p-5" aria-label={t("admin.salesTrend")}>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h3 className="font-semibold text-foreground">{t("admin.salesTrend")}</h3>
          <p className="mt-1 text-xs text-muted-foreground">{trends[0]?.date} ~ {trends.at(-1)?.date}</p>
          <p className="mt-3 text-2xl font-semibold tabular-nums">{format(total)}</p>
        </div>
        <div className="flex border-b border-border" role="group" aria-label={t("admin.salesTrend")}>
          {(["sales_amount", "order_count"] as const).map((key) => (
            <button key={key} type="button" aria-pressed={metric === key} onClick={() => setMetric(key)}
              className={`min-h-10 border-b-2 px-3 text-sm ${metric === key ? "border-primary font-semibold text-foreground" : "border-transparent text-muted-foreground"}`}>
              {t(key === "sales_amount" ? "admin.salesAmount" : "admin.orderCount")}
            </button>
          ))}
        </div>
      </div>
      <div className="relative mt-5 h-72 w-full min-w-0">
        {total === 0 && <p className="pointer-events-none absolute inset-x-0 top-20 z-10 text-center text-sm text-muted-foreground">{t("admin.noOrderData")}</p>}
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={trends} margin={{ top: 16, right: 8, left: 0, bottom: 8 }} barCategoryGap="30%">
            <CartesianGrid vertical={false} strokeDasharray="3 3" stroke="hsl(var(--border))" />
            <XAxis dataKey="date" tickFormatter={(value: string) => value.slice(5)} minTickGap={24} tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} axisLine={false} tickLine={false} />
            <YAxis width={58} domain={[0, (max: number) => Math.max(1, max)]} allowDecimals={sales} tickFormatter={(value: number) => sales ? `¥${value}` : String(value)} tick={{ fontSize: 11, fill: "hsl(var(--muted-foreground))" }} axisLine={false} tickLine={false} />
            <Tooltip cursor={false} isAnimationActive={false} formatter={(value: number) => [format(value), label]} contentStyle={{ background: "hsl(var(--card))", color: "hsl(var(--foreground))", border: "1px solid hsl(var(--border))", borderRadius: 6, fontSize: 12 }} />
            <Bar dataKey={metric} name={label} fill={sales ? "#3b82f6" : "#10b981"} maxBarSize={32} radius={[3, 3, 0, 0]} isAnimationActive={false} activeBar={false} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </section>
  )
}
