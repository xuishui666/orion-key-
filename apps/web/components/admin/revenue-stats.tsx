"use client"

import { useState, useEffect } from "react"
import { Search } from "lucide-react"
import { toast } from "sonner"
import { useLocale } from "@/lib/context"
import { adminOrderApi, getApiErrorMessage } from "@/services/api"

export function RevenueStats() {
  const { t } = useLocale()
  const [startDate, setStartDate] = useState("")
  const [endDate, setEndDate] = useState("")
  const [revenue, setRevenue] = useState<Awaited<ReturnType<typeof adminOrderApi.getRevenueStats>> | null>(null)
  const [statsLoading, setStatsLoading] = useState(false)
  const fetchRevenue = async () => {
    if (startDate && endDate && startDate > endDate) { toast.error("开始日期不能晚于结束日期"); return }
    setRevenue(null)
    setStatsLoading(true)
    try {
      setRevenue(await adminOrderApi.getRevenueStats({ start_date: startDate || undefined, end_date: endDate || undefined }))
    } catch (err) { toast.error(getApiErrorMessage(err, t)) }
    finally { setStatsLoading(false) }
  }

  useEffect(() => { fetchRevenue() }, [])
  return (
      <section className="space-y-3 border-y border-border py-4">
        <h2 className="text-base font-semibold">销售额统计</h2>
        <div className="flex flex-wrap items-end gap-3">
          <label className="text-sm">开始日期<input aria-label="开始日期" type="date" value={startDate} onChange={e => setStartDate(e.target.value)} className="block rounded border border-input bg-background p-2" /></label>
          <label className="text-sm">结束日期<input aria-label="结束日期" type="date" value={endDate} onChange={e => setEndDate(e.target.value)} className="block rounded border border-input bg-background p-2" /></label>
          <button type="button" disabled={statsLoading} onClick={fetchRevenue} className="flex items-center gap-2 rounded border border-input p-2 text-sm disabled:opacity-50"><Search className="h-4 w-4" />查询</button>
        </div>
        {revenue && <div className="space-y-3 text-sm">
          <p>销售额：¥{Number(revenue.total_amount).toFixed(2)} · 已付款订单：{revenue.order_count}</p>
          <div className="grid gap-4 sm:grid-cols-2">
            <div><h3 className="font-medium">按渠道</h3>{revenue.by_payment_method.map((row, i) => <p key={i} className="flex justify-between gap-3 border-b py-2"><span>{row.payment_method}</span><span>¥{Number(row.amount).toFixed(2)} · {row.count} 单</span></p>)}</div>
            <div><h3 className="font-medium">按商品</h3>{revenue.by_product.map((row, i) => <p key={i} className="flex justify-between gap-3 border-b py-2"><span className="break-all">{row.product_title}</span><span className="shrink-0">¥{Number(row.amount).toFixed(2)} · {row.count} 件</span></p>)}</div>
          </div>
        </div>}
      </section>

  )
}

