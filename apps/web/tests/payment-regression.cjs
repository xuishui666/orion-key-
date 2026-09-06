const assert = require("node:assert/strict")
const fs = require("node:fs")
const path = require("node:path")
const vm = require("node:vm")
const ts = require("typescript")

function load(file, globals = {}, imports = {}) {
  const exports = {}
  const source = fs.readFileSync(path.join(__dirname, "..", file), "utf8")
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText
  vm.runInNewContext(code, { exports, require: name => imports[name] || require(name), URL, URLSearchParams, process, console, Error, ...globals })
  return exports
}

async function main() {
  const blockedWindow = {}
  for (const name of ["localStorage", "sessionStorage"]) {
    Object.defineProperty(blockedWindow, name, { get() { throw new Error("Storage blocked") } })
  }
  const utils = load("lib/utils.ts", { window: blockedWindow })
  utils.safeStorageSet("sessionStorage", "token", "value")
  assert.equal(utils.safeStorageGet("sessionStorage", "token"), "value")
  utils.safeStorageRemove("sessionStorage", "token")
  assert.equal(utils.safeStorageGet("sessionStorage", "token"), null)
  assert.equal(utils.safePaymentUrl("javascript:alert(1)"), "")
  assert.equal(utils.safePaymentUrl("data:text/html,test"), "")
  assert.equal(utils.safePaymentUrl("alipays://platformapi/startapp"), "alipays://platformapi/startapp")
  const calls = []
  const api = load("services/api.ts", {
    fetch: async (url, options) => {
      calls.push({ url, options })
      await Promise.resolve()
      return { ok: true, headers: { get: () => null }, json: async () => ({ code: 0, data: {} }) }
    }
  }, { "@/lib/utils": utils })
  await Promise.all([
    api.orderApi.create({}, "order-token"),
    api.authApi.login({}, "login-token"),
    api.orderApi.getStatus("test")
  ])
  assert.equal(calls.find(c => c.url.endsWith("/orders")).options.headers["X-Turnstile-Token"], "order-token")
  assert.equal(calls.find(c => c.url.endsWith("/auth/login")).options.headers["X-Turnstile-Token"], "login-token")
  assert.equal(calls.find(c => c.url.endsWith("/status")).options.headers["X-Turnstile-Token"], undefined)
  assert.ok(api.getApiErrorMessage(new Error("   "), () => "").trim())
  await assert.rejects(api.withMockFallback(async () => { throw new Error("Offline") }, () => ({ fake: true })))
  console.log("Payment regression checks passed")
}
main().catch(error => { console.error(error); process.exitCode = 1 })
