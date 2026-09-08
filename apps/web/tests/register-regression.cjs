const assert = require("node:assert/strict")
const fs = require("node:fs")
const path = require("node:path")
const ts = require("typescript")
const vm = require("node:vm")

const source = fs.readFileSync(path.join(__dirname, "../app/(store)/register/page.tsx"), "utf8")
const body = source.match(/const fetchCaptcha = useCallback\(async \(\) => \{([\s\S]*?)\n  \}, \[t\]\)/)[1]
const code = ts.transpile(`(async () => {${body}})()`, { target: ts.ScriptTarget.ES2022 })

async function check(fail) {
  const state = { id: "old", image: "old", form: { captcha: "old" }, loading: false, error: "" }
  await vm.runInNewContext(code, {
    setCaptchaLoading: value => state.loading = value,
    setCaptchaId: value => state.id = value,
    setCaptchaImage: value => state.image = value,
    setForm: update => state.form = update(state.form),
    authApi: { getCaptcha: async () => { if (fail) throw new Error("Offline"); return { captcha_id: "new", captcha_image: "real-image" } } },
    toast: { error: value => state.error = value },
    getApiErrorMessage: error => error.message,
    t: value => value,
  })
  assert.equal(state.loading, false)
  assert.equal(state.form.captcha, "")
  assert.equal(state.id, fail ? "" : "new")
  assert.equal(state.image, fail ? "" : "real-image")
  assert.equal(state.error, fail ? "Offline" : "")
}
Promise.resolve().then(() => check(false)).then(() => check(true)).then(() => {
  assert.ok(source.includes("if (isLoading || captchaLoading || !captchaId) return"))
  assert.ok(!source.includes("mockCaptcha"))
  console.log("Registration regression checks passed")
}).catch(error => { console.error(error); process.exitCode = 1 })

