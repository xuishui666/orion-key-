# Payment and Admin Changes

Target: xuishui666/orion-key- only. No production deployment was performed.

## Changes

- Turnstile tokens belong to individual requests. Submission waits for site configuration and verification. Script failures/timeouts offer retry.
- Orders and items commit together before gateway calls. Gateway errors preserve the order and return its ID for retry.
- Payment context updates preserve callback-driven order status. USDT retries reuse existing payment context.
- QR payloads are no longer used as mobile redirect targets. Device detail is normalized to gateway-supported values. Only HTTP(S) links auto-redirect; supported app schemes require a click.
- Payment status restores method, QR/H5 context and USDT wallet, amount and chain. Query-string wallet and redirect overrides are ignored.
- Restricted browser storage has an in-memory fallback, including authentication and payment flags. It cannot survive a full reload when browser storage is entirely blocked.
- Blank/whitespace error messages have a fallback. Production network failures cannot create mock orders or fake login success. Mock fallback requires development mode and NEXT_PUBLIC_ENABLE_MOCKS=true.
- Unconfirmed chain transactions go to review. Failed chain checks do not consume callback idempotency keys, so a later confirmed callback can retry. Signature, amount and token checks remain mandatory.
- Admin order soft delete and batch delete preserve financial records and callback processing. Card deletion hides unsold keys and removes them from available inventory; sold keys are retained for delivery history.
- Revenue supports inclusive date ranges and channel/product breakdowns. Paid and delivered orders count, including hidden orders. Product breakdown uses item subtotals; total/channel figures use actual paid amounts.

## Verification

- Backend: Java 22, Maven, H2 PostgreSQL mode; application startup and payment regression tests.
- Frontend: production build, separate TypeScript check, and node apps/web/tests/payment-regression.cjs.
- Automated checks do not replace real gateway, PostgreSQL concurrency or physical Safari/WebView testing.

## Before Deployment

1. Back up PostgreSQL, current images, configuration and uploads; confirm the backup can be restored.
2. Run apps/api/migrations/20260906_soft_delete.sql against the test database, then the production database during the approved deployment window. The migration adds two defaulted columns and is repeatable.
3. Verify Turnstile site/secret keys, domain allowlist, payment credentials, HTTPS notify/return URLs and server time zone.
4. Test a low-value order per enabled payment channel: desktop QR, Safari/Chrome mobile, and embedded browsers. Cancel, return, refresh and reopen the payment page.
5. Force gateway timeout and confirm the order/items remain, with retry on the same order. Verify USDT wallet/amount/chain after refreshing a URL without payment query parameters.
6. Test duplicate callbacks, delayed confirmations and chain API outages. Confirm only one payment is recorded; unverified transfers must not auto-credit.
7. Test single/batch deletion, sold-key protection, inventory counts and revenue date boundaries. Hidden paid orders must remain in revenue.
8. Use PostgreSQL staging to test concurrent payment callbacks, expiration, deletion and fulfillment. H2 does not reproduce all PostgreSQL locking behavior.
9. Build from the reviewed branch commit. Deploy only after the above checks; retain the previous images for rollback. Do not drop the added columns during an application rollback.

## Commands

```sh
cd apps/api && mvn test
cd ../.. && pnpm --filter @orion-key/web build
pnpm --filter @orion-key/web exec tsc --noEmit
node apps/web/tests/payment-regression.cjs
```
