import { safeStorageGet, safeStorageSet } from "@/lib/utils"

const CACHE_KEY = "__device_id_v2__"

/** Return a random browser installation ID instead of a collision-prone hardware fingerprint. */
export async function getDeviceId(): Promise<string> {
  if (typeof window === "undefined") return ""

  const cached = safeStorageGet("localStorage", CACHE_KEY)
  if (cached) return cached

  try {
    const bytes = crypto.getRandomValues(new Uint8Array(32))
    const id = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("")
    safeStorageSet("localStorage", CACHE_KEY, id)
    return id
  } catch {
    return ""
  }
}
