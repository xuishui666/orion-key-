import { Megaphone } from "lucide-react"
import ReactMarkdown from "react-markdown"

interface HomeAnnouncementProps {
  title: string
  body: string
  font?: string
  size?: string
  color?: string
  groupUrl?: string
  groupLabel?: string
}

const fonts: Record<string, string> = {
  sans: "ui-sans-serif, system-ui, sans-serif",
  serif: "ui-serif, Georgia, serif",
  mono: "ui-monospace, SFMono-Regular, monospace",
}

export function HomeAnnouncement({ title, body, font, size, color, groupUrl, groupLabel }: HomeAnnouncementProps) {
  const bodySize = Number.isInteger(Number(size)) && Number(size) >= 14 && Number(size) <= 22 ? Number(size) : 16
  const textColor = color && /^#[0-9a-fA-F]{6}$/.test(color) ? color : undefined

  return (
    <section aria-label="首页公告" className="border-y border-border bg-card/60 px-5 py-5 sm:px-8 sm:py-6">
      <div className="flex items-center gap-2 text-sm font-semibold text-muted-foreground">
        <Megaphone className="h-4 w-4" aria-hidden="true" />
        <span>公告</span>
      </div>
      <div className="mt-3 break-words" style={{ fontFamily: fonts[font ?? ""] ?? fonts.sans, color: textColor }}>
        {title && <h1 className="font-bold leading-tight" style={{ fontSize: bodySize + 10 }}>{title}</h1>}
        {body && (
          <div className="mt-2 space-y-2 leading-relaxed" style={{ fontSize: bodySize }}>
            <ReactMarkdown
              allowedElements={["p", "strong", "em", "a", "ul", "ol", "li", "br"]}
              unwrapDisallowed
              components={{
                a: ({ href, children }) => /^https?:\/\//i.test(href ?? "")
                  ? <a href={href} target="_blank" rel="noopener noreferrer" className="underline underline-offset-2">{children}</a>
                  : <span>{children}</span>,
                ul: ({ children }) => <ul className="list-disc pl-5">{children}</ul>,
                ol: ({ children }) => <ol className="list-decimal pl-5">{children}</ol>,
              }}
            >{body}</ReactMarkdown>
          </div>
        )}
      </div>
      {groupUrl && /^https?:\/\//i.test(groupUrl) && (
        <a href={groupUrl} target="_blank" rel="noopener noreferrer"
          className="mt-4 inline-flex items-center gap-1.5 text-sm font-semibold text-[#2AABEE] underline underline-offset-2">
          <img src="/images/telegram.png" alt="" className="h-4 w-4" />
          {groupLabel}
        </a>
      )}
    </section>
  )
}
