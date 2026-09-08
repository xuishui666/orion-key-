"use client"

import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from "recharts"
import { useLocale } from "@/lib/context"
import type { SalesTrend } from "@/types"

export function SalesChart({ trends }: { trends: SalesTrend[] }) {
  const { t } = useLocale()
  return (
    <div className="rounded-xl border border-border bg-card p-5 shadow-sm">
      <div className="mb-6 flex items-center justify-between">
        <h3 className="font-semibold text-foreground">{t("admin.salesTrend")}</h3>
        <div className="flex gap-4 text-xs text-muted-foreground">
          <span className="flex items-center gap-1.5">
            <span className="h-2 w-2 rounded-full bg-blue-500" />
            {t("admin.salesAmount")}
          </span>
          <span className="flex items-center gap-1.5">
            <span className="h-2 w-2 rounded-full bg-emerald-500" />
            {t("admin.orderCount")}
          </span>
        </div>
      </div>
      <div className="h-72">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={trends} margin={{ top: 5, right: 5, left: 0, bottom: 0 }}>
            <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" />
            <XAxis
              dataKey="date"
              tick={{ fontSize: 12, fill: "hsl(var(--muted-foreground))" }}
              axisLine={{ stroke: "hsl(var(--border))" }}
              tickLine={false}
            />
            <YAxis
              yAxisId="sales"
              tick={{ fontSize: 12, fill: "hsl(var(--muted-foreground))" }}
              axisLine={false}
              tickLine={false}
            />
            <YAxis yAxisId="orders" orientation="right" allowDecimals={false} tick={{ fontSize: 12 }} axisLine={false} tickLine={false} />
            <Tooltip
              contentStyle={{
                background: "hsl(var(--card))",
                border: "1px solid hsl(var(--border))",
                borderRadius: "8px",
                fontSize: "12px",
              }}
            />
            <Bar
              yAxisId="sales"
              isAnimationActive={false}
              maxBarSize={24}
              dataKey="sales_amount"
              fill="#3b82f6"
              name={`${t("admin.salesAmount")} (¥)`}
            />
            <Bar
              yAxisId="orders"
              isAnimationActive={false}
              maxBarSize={24}
              dataKey="order_count"
              fill="#10b981"
              name={t("admin.orderCount")}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  )
}

