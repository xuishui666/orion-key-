import { getProducts, getCategories, getSiteConfig } from "@/services/api-server"
import { HomeContent } from "./home-content"
import type { Metadata } from "next"

export async function generateMetadata(): Promise<Metadata> {
  try {
    const config = await getSiteConfig()
    return {
      title: config.site_name || "Orion Key",
      description: config.home_notice_body?.replace(/\s+/g, " ").trim() || "",
      alternates: { canonical: "/" },
      openGraph: {
        title: config.site_name || "Orion Key",
        description: config.home_notice_body?.replace(/\s+/g, " ").trim() || "",
        url: "/",
        ...(config.logo_url ? { images: [{ url: config.logo_url }] } : {}),
      },
    }
  } catch {
    return { title: "Orion Key" }
  }
}

export default async function HomePage() {
  const [productsData, categories, config] = await Promise.all([
    getProducts({ page: 1, page_size: 100 }).catch(() => ({ list: [] as never[], pagination: { page: 1, page_size: 100, total: 0 } })),
    getCategories().catch(() => []),
    getSiteConfig().catch(() => null),
  ])

  const jsonLd = {
    "@context": "https://schema.org",
    "@type": "WebSite",
    name: config?.site_name || "Orion Key",
    description: config?.home_notice_body?.replace(/\s+/g, " ").trim() || "",
  }

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
      />
      <HomeContent
        products={productsData.list}
        categories={categories}
        noticeTitle={config?.home_notice_title}
        noticeBody={config?.home_notice_body}
        noticeFont={config?.home_notice_font}
        noticeSize={config?.home_notice_size}
        noticeColor={config?.home_notice_color}
      />
    </>
  )
}
