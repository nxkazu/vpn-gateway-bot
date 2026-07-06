Tessera VPN Bot - Architecture & Codebase Analysis

Below is the analysis. You can include it in the project root.
Here is an expert architectural analysis of the Tessera VPN Telegram Bot application based on the provided source code dumps.

---

## 1. Architectural Overview & Flow

Tessera VPN is a monolithic Java application acting as a middleware/orchestrator between Telegram (the user interface), Remnawave (the VPN panel/backend), various payment gateways, and a local PostgreSQL database (the source of truth for billing, business logic, and user state).

### Core Layers:
*   **Transport Layer (Telegram):** Uses long-polling (`TelegramLongPollingBot`) to receive updates. Routes commands, callbacks, and native payment events.
*   **Routing & State (Handlers):** `TesseraBot`, `UserHandler`, and `AdminHandler` manage the conversational state and route user intents to services.
*   **Business Logic (Services):** 
    *   `SubscriptionService`: Syncs local user state with the Remnawave panel.
    *   `PaymentService` & `PaymentWatcher`: Manages the lifecycle of invoices (Creation → Polling/Webhooks → Idempotent Fulfillment).
    *   Additional services manage Promos, Gifts, Referrals, and Support.
*   **Integration Layer:**
    *   `RemnawaveClient`: Sync HTTP client wrapping the VPN control panel.
    *   Payment Providers: Implementations for CryptoBot, Platega, and Telegram Stars.
*   **Data Persistence:** PostgreSQL handles relational data, utilizing `TIMESTAMPTZ`, `JSONB` for analytics, and `UUID` for system linking.

---

## 2. Completeness of Features

**What is well-implemented and complete:**
*   **Database Schema:** Excellent use of relational mapping. The evolution from V1 to V8 shows a mature product adding enterprise features (Audit trails, Promos, Gifts, Event analytics).
*   **Idempotency & Payment Safety:** `PaymentService.checkAndActivate` uses atomic DB claims (`invoices.markPaidIfPending`) before granting rewards/VPN access. This perfectly prevents race conditions between user manual checks, webhooks, and the background `PaymentWatcher`.
*   **Configuration Management:** Immutable, environment-variable-driven config (`AppConfig`) with robust admin role matrix parsing.
*   **Localization (i18n):** Simple but effective properties-based fallback system.
*   **Extensibility:** The `PaymentProvider` interface is clean, allowing easy addition of new gateways.

**What seems stubbed, missing, or cut off:**
*   **PromoService:** The code cuts off at the end of the dump, but it is heavily relied upon in the `PaymentService`.
*   **Transactional Boundaries:** There is no visible `@Transactional` annotation or manual transaction management in `PaymentService`. 
*   **Rate Limiter & UserState internals:** The implementations weren't provided. If they are in-memory (e.g., `ConcurrentHashMap`), this is a scaling bottleneck.
*   **WebApp:** The configuration mentions WebApp settings (`WEBAPP_ENABLED`, `webappInitDataMaxAgeSeconds`), but there are no handlers or endpoints visible in the dump.

---

## 3. Missing Logic, Potential Bugs & Edge Cases

### A. Critical Bug: Telegram Stars State Loss
In `TelegramStarsProvider`, paid invoices are stored in an in-memory `Set<String> confirmed`. 
*   **The Issue:** Telegram Stars do not have a server-side polling API. The *only* proof of payment is the `successful_payment` update. If the bot crashes or restarts between receiving the update and fulfilling the order, or if the `checkAndActivate` process throws an exception, the in-memory `confirmed` set is wiped. The user's payment will be permanently lost with no way to recover it via polling.
*   **Fix:** `TelegramStarsProvider` must persist confirmations immediately to the database (e.g., a `stars_confirmations` table) instead of RAM.

### B. Blocking I/O in the Telegram Polling Thread
`TesseraBot` handles updates synchronously. When a user clicks a button, the bot might call the DB, make an HTTP call to Platega, and make an HTTP call to Remnawave.
*   **The Issue:** If Remnawave or Platega APIs experience latency (e.g., a 10-second timeout), the Telegram bot thread processing that update is blocked. Under load, this will exhaust the bot's thread pool, causing it to freeze and stop responding to all users.
*   **Fix:** Dispatch business logic (specifically network calls) to a separate `ExecutorService` or use asynchronous handlers (`CompletableFuture`).

### C. Missing Database Index on Invoices
`PaymentWatcher` queries `PENDING` invoices up to 24 hours old every few seconds.
*   **The Issue:** The `payment_invoices` table only has indexes on `telegram_id` and `(provider, external_id)`. Polling by `status` and `created_at` will result in a full table scan, degrading DB performance as the table grows.
*   **Fix:** Add `CREATE INDEX idx_invoices_status_created ON payment_invoices(status, created_at);`

### D. Missing Database Transactions
In `PaymentService.checkAndActivate`, upon a `PAID` status, the system:
1. Marks invoice paid.
2. Activates Remnawave plan (Network Call).
3. Records payment ledger.
4. Redeems promo code.
5. Rewards referrer.
*   **The Issue:** If the DB crashes at step 4, the user got the VPN, but the promo code wasn't marked as used, and the ledger wasn't written. 
*   **Fix:** Wrap DB mutations (steps 1, 3, 4, 5) in a DB transaction. 

### E. Remnawave API Failure Handling
If `checkAndActivate` successfully marks the invoice as paid, but the `subscriptions.activatePlan()` network call fails (e.g., Remnawave is down), the catch block reverts the invoice to `PENDING`. 
*   **The Issue:** For Telegram Stars, if it reverts to PENDING, it relies on the in-memory map to retry later. If the app restarts, it's stuck. 
*   **Fix:** Separate the "Payment Confirmed" state from the "Fulfillment" state. The invoice should stay `PAID`, but an `activations_queue` table should handle retrying Remnawave provisioning asynchronously.

---

## 4. Recommendations for Production Readiness

To move this from a solid prototype to an enterprise-grade, production-ready system, implement the following:

1.  **State Externalization (Horizontal Scalability):**
    *   Move `UserState` (which tracks `/support` prompts, promo codes, etc.) to Redis or PostgreSQL. If the bot restarts, users shouldn't lose their conversation context.
    *   Run the bot in Webhook mode rather than Long-Polling if deploying to a multi-instance Kubernetes/Docker setup.

2.  **Resilient Provisioning Queue:**
    *   Do not provision VPN accounts synchronously in the payment thread. Once an invoice is confirmed `PAID`, drop a message into a local queue or DB table. A separate background worker should attempt to provision the Remnawave user. This ensures users *always* get their VPN eventually, even if Remnawave is undergoing maintenance during their checkout.

3.  **Database Optimizations:**
    *   Add the missing index: `CREATE INDEX idx_invoices_polling ON payment_invoices(status, created_at) WHERE status = 'PENDING';` (A partial index is highly efficient here).
    *   Enforce database-level transactions across Ledger, Promos, and Referrals.

4.  **Graceful Degradation & Timeouts:**
    *   In `RemnawaveClient`, the timeouts are set to 15-45 seconds. This is too long for a Telegram bot UI (Telegram shows a "loading" clock on buttons, which times out and fails on the UI side after ~10 seconds). Reduce OkHttp timeouts to 5-8 seconds.

5.  **Observability & Monitoring:**
    *   `AppConfig` mentions `opsPort` and `opsHealthPublic`. Ensure you are exposing Prometheus metrics. Specifically, track:
        *   Payment provider API latency.
        *   Remnawave API error rates.
        *   Pending invoice queue size.
        *   Thread pool exhaustion.

6.  **i18n Pluralization:**
    *   Currently, `Messages` only supports standard substitution. You will quickly run into issues displaying "1 day" vs "3 days" vs "5 days" (especially in Russian, which has complex pluralization rules: 1 день, 3 дня, 5 дней). Consider integrating standard `MessageFormat` or a lightweight pluralization engine.