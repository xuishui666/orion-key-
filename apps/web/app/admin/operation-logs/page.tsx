"use client"

import { useState, useEffect } from "react"
import { Trash2, ChevronDown, ChevronLeft, ChevronRight } from "lucide-react"
import { toast } from "sonner"
import { cn } from "@/lib/utils"
import { useLocale } from "@/lib/context"
import { adminLogApi, withMockFallback, getApiErrorMessage } from "@/services/api"
import { mockOperationLogList } from "@/lib/mock-data"
import type { OperationLog } from "@/types"

const actionLabels: Record<string, { label: string; color: string }> = {
  "log.delete": { label: "删除日志", color: "bg-red-500/10 text-red-500" },
  "product.create": { label: "创建商品", color: "bg-emerald-500/10 text-emerald-600" },
  "product.update": { label: "修改商品", color: "bg-blue-500/10 text-blue-600" },
  "product.delete": { label: "删除商品", color: "bg-red-500/10 text-red-500" },
  "cardkey.import": { label: "导入卡密", color: "bg-violet-500/10 text-violet-600" },
  "cardkey.invalidate": { label: "作废卡密", color: "bg-red-500/10 text-red-500" },
  "order.mark_paid": { label: "手动标记", color: "bg-amber-500/10 text-amber-600" },
  "user.disable": { label: "禁用用户", color: "bg-red-500/10 text-red-500" },
  "user.enable": { label: "启用用户", color: "bg-emerald-500/10 text-emerald-600" },
  "config.update": { label: "更新配置", color: "bg-blue-500/10 text-blue-600" },
  "category.create": { label: "创建分类", color: "bg-emerald-500/10 text-emerald-600" },
  "category.update": { label: "修改分类", color: "bg-blue-500/10 text-blue-600" },
  "category.delete": { label: "删除分类", color: "bg-red-500/10 text-red-500" },
  "payment.create": { label: "添加支付", color: "bg-emerald-500/10 text-emerald-600" },
  "payment.update": { label: "更新支付", color: "bg-blue-500/10 text-blue-600" },
  "payment.delete": { label: "删除支付", color: "bg-red-500/10 text-red-500" },
  "user.toggle": { label: "切换用户状态", color: "bg-amber-500/10 text-amber-600" },
  "txid.approve": { label: "审核通过TXID", color: "bg-emerald-500/10 text-emerald-600" },
  "txid.reject": { label: "审核拒绝TXID", color: "bg-red-500/10 text-red-500" },
}

const ITEMS_PER_PAGE = 20

export default function AdminOperationLogsPage() {
  const { t } = useLocale()
  const [logs, setLogs] = useState<OperationLog[]>([])
  const [total, setTotal] = useState(0)
  const [loading, setLoading] = useState(true)
  const [actionFilter, setActionFilter] = useState("")
  const [currentPage, setCurrentPage] = useState(1)
  const [selected, setSelected] = useState<string[]>([])
  const [deleting, setDeleting] = useState(false)

  const deleteLogs = async (ids: string[]) => {
    if (deleting || !ids.length || !window.confirm(`永久删除这 ${ids.length} 条操作日志？删除后无法恢复。`)) return
    setDeleting(true)
    try {
      const result = await adminLogApi.batchDelete(ids)
      toast.success(`已删除 ${result.deleted_count} 条日志`)
      setSelected([])
      if (currentPage > 1) setCurrentPage(1)
      else await fetchLogs()
    } catch (err) { toast.error(getApiErrorMessage(err, t)) }
    finally { setDeleting(false) }
  }

  const fetchLogs = async () => {
    setLoading(true)
    setSelected([])
    try {
      const data = await withMockFallback(
        () => adminLogApi.getList({
          page: currentPage,
          page_size: ITEMS_PER_PAGE,
          action: actionFilter || undefined,
        }),
        () => mockOperationLogList({
          page: currentPage,
          page_size: ITEMS_PER_PAGE,
        })
      )
      setLogs(data.list)
      setTotal(data.pagination.total)
    } catch (err) {
      toast.error(getApiErrorMessage(err, t))
      setLogs([])
      setTotal(0)
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { fetchLogs() }, [currentPage, actionFilter])

  const totalPages = Math.ceil(total / ITEMS_PER_PAGE)

  return (
    <div className="flex flex-col gap-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-foreground">{t("admin.logs")}</h1>
        <p className="text-sm text-muted-foreground">{t("admin.logsDesc")}</p>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3">
        <div className="relative">
          <select
            disabled={deleting}
            className="h-10 appearance-none rounded-lg border border-input bg-background pl-3 pr-8 text-sm text-foreground focus:outline-none focus:ring-2 focus:ring-ring"
            value={actionFilter}
            onChange={(e) => { setActionFilter(e.target.value); setCurrentPage(1) }}
          >
            <option value="">{t("admin.allActionTypes")}</option>
            <option value="product">{t("admin.productOps")}</option>
            <option value="cardkey">{t("admin.cardKeyOps")}</option>
            <option value="order">{t("admin.orderOps")}</option>
            <option value="user">{t("admin.userOps")}</option>
            <option value="config">{t("admin.configOps")}</option>
            <option value="category">{t("admin.categoryOps")}</option>
            <option value="payment">{t("admin.paymentOps")}</option>
            <option value="txid">{t("admin.txidOps")}</option>
            <option value="log">删除日志</option>
          </select>
          <ChevronDown className="pointer-events-none absolute right-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
        </div>
      </div>

      {/* Table */}
      <button type="button" disabled={loading || deleting || !selected.length} onClick={() => deleteLogs(selected)} className="flex w-fit items-center gap-2 rounded border border-input px-3 py-2 text-sm text-destructive disabled:opacity-50"><Trash2 className="h-4 w-4" />批量删除 ({selected.length})</button>
      <div className="overflow-hidden rounded-xl border border-border bg-card shadow-sm">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-border bg-muted/30">
                <th className="px-3"><input type="checkbox" aria-label="选择本页日志" disabled={loading || deleting || !logs.length} checked={logs.length > 0 && selected.length === logs.length} onChange={e => setSelected(e.target.checked ? logs.map(log => log.id) : [])} /></th>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">{t("admin.time")}</th>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">{t("admin.operator")}</th>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">{t("admin.actionType")}</th>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">{t("admin.targetLabel")}</th>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">{t("admin.detailLabel")}</th>
                <th className="px-4 py-3 text-left font-medium text-muted-foreground">{t("admin.ipLabel")}</th>
                <th className="px-3 py-3 text-left">操作</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan={8} className="py-12">
                    <div className="flex items-center justify-center">
                      <div className="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
                    </div>
                  </td>
                </tr>
              ) : logs.length === 0 ? (
                <tr>
                  <td colSpan={8} className="py-8 text-center text-sm text-muted-foreground">{t("admin.noLogData")}</td>
                </tr>
              ) : (
                logs.map((log) => {
                  const action = actionLabels[log.action]
                  return (
                    <tr key={log.id} className="border-b border-border/50 last:border-0 hover:bg-muted/20 transition-colors">
                      <td className="px-3"><input type="checkbox" aria-label={`选择日志 ${log.id}`} disabled={deleting} checked={selected.includes(log.id)} onChange={e => setSelected(ids => e.target.checked ? [...ids, log.id] : ids.filter(id => id !== log.id))} /></td>
                      <td className="px-4 py-3 text-xs text-muted-foreground whitespace-nowrap">
                        {new Date(log.created_at).toLocaleString()}
                      </td>
                      <td className="px-4 py-3 font-medium text-foreground">{log.username}</td>
                      <td className="px-4 py-3">
                        <span className={cn("rounded-full px-2.5 py-0.5 text-xs font-medium", action?.color || "bg-muted text-foreground")}>
                          {action?.label || log.action}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-foreground">
                        {log.target_type}
                        {log.target_id && (
                          <span className="ml-1 font-mono text-xs text-muted-foreground">
                            ({log.target_id.length > 12 ? `${log.target_id.slice(0, 8)}...` : log.target_id})
                          </span>
                        )}
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">{log.detail || "-"}</td>
                      <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{log.ip_address}</td>
                      <td className="px-3"><button type="button" title="删除日志" aria-label="删除日志" disabled={deleting} onClick={() => deleteLogs([log.id])} className="rounded p-2 text-destructive disabled:opacity-50"><Trash2 className="h-4 w-4" /></button></td>
                    </tr>
                  )
                })
              )}
            </tbody>
          </table>
        </div>
        <div className="flex items-center justify-between border-t border-border px-4 py-3">
          <span className="text-sm text-muted-foreground">{t("admin.totalRecords")} {total} {t("admin.records")}</span>
          {totalPages > 1 && (
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => setCurrentPage(p => Math.max(1, p - 1))}
                disabled={currentPage === 1}
                className="flex h-8 w-8 items-center justify-center rounded-md border border-input text-muted-foreground hover:bg-accent disabled:opacity-50"
              >
                <ChevronLeft className="h-4 w-4" />
              </button>
              {Array.from({ length: totalPages }, (_, i) => i + 1)
                .filter(page => page === 1 || page === totalPages || Math.abs(page - currentPage) <= 1)
                .map((page, index, array) => (
                  <div key={page} className="flex items-center gap-1">
                    {index > 0 && array[index - 1] !== page - 1 && (
                      <span className="px-1 text-muted-foreground">...</span>
                    )}
                    <button
                      type="button"
                      onClick={() => setCurrentPage(page)}
                      className={cn(
                        "flex h-8 w-8 items-center justify-center rounded-md text-sm font-medium",
                        currentPage === page
                          ? "bg-primary text-primary-foreground"
                          : "border border-input text-foreground hover:bg-accent"
                      )}
                    >
                      {page}
                    </button>
                  </div>
                ))}
              <button
                type="button"
                onClick={() => setCurrentPage(p => Math.min(totalPages, p + 1))}
                disabled={currentPage === totalPages}
                className="flex h-8 w-8 items-center justify-center rounded-md border border-input text-muted-foreground hover:bg-accent disabled:opacity-50"
              >
                <ChevronRight className="h-4 w-4" />
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

