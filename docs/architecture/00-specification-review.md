# 00 — Specification Review

Status: **Accepted baseline** · Applies to: all phases · Owner: Architecture

This review covers the *Master Build Prompt* (85 sections). It lists the risks and gaps that need handling before code is written, and the changes adopted in the final architecture ([01-system-architecture.md](01-system-architecture.md)).

Severity: **H** = would cause financial loss, data leakage or a rewrite if ignored · **M** = significant rework or operational pain · **L** = quality / clarity.

---

## 1. Overall assessment

The specification is strong on intent. Ledger-first thinking, idempotency, maker-checker, tenant isolation, "never delete posted transactions" and the phased plan are all correct instincts. In scope it is a full **core banking system** (CBS), comparable to Apache Fineract, Mambu or Temenos Transact, plus two mobile channels, two web consoles, a payments hub and a compliance suite.

The main delivery risk is **breadth over depth**. Phases 1 and 2 must ship a *small* surface area whose correctness is proven before anything else is added. Every phase in [04-roadmap.md](04-roadmap.md) has an exit gate for this reason.

---

## 2. Architectural risks

| # | Sev | Risk | Adopted mitigation |
|---|-----|------|--------------------|
| A1 | H | **Tenant isolation enforced only in application code.** One missing `WHERE tenant_id = ?` leaks another institution's customers. | Three layers: (1) the tenant comes **only** from the authenticated token, never from request input; (2) repositories always scope by tenant; (3) **PostgreSQL Row-Level Security** on every tenant-owned table, with the tenant id set per DB transaction (`set_config('app.tenant_id', …, true)`). The runtime DB role is not the table owner and has no `BYPASSRLS`. Composite foreign keys `(tenant_id, x_id)` make cross-tenant *references* impossible. |
| A2 | H | **Module boundaries erode** in a monolith of ~30 packages. Within a year everything calls everyone's repositories and the "modular monolith" becomes a big ball of mud. | Modules talk only through a module's public `service` API or domain events. Entities never hold JPA associations across modules (store IDs). Enforced by **ArchUnit tests** in CI (no cycles, controllers never touch repositories, no cross-module repository access). |
| A3 | H | **"Transaction" and "ledger" overlap.** The spec lists `transaction`, `transaction_entry` *and* `journal_entry`, `ledger_entry`. Two sources of truth for money. | `financial_transaction` = the business intent with a lifecycle (INITIATED → POSTED / FAILED / REVERSED). `journal_entry` + `ledger_entry` = the immutable accounting record. **`transaction_entry` is dropped**; `ledger_entry` is the only line-level record. One **Posting Engine** is the only code allowed to write the ledger. |
| A4 | H | **Hot-row contention on GL balances.** If every posting updates a "Customer Deposits 2110" balance row, all transactions in a tenant serialize on that row. | Only **sub-ledger** balances (customer accounts, teller drawers, vaults) are materialized and locked synchronously, because available-balance checks need them. GL balances are **derived** (aggregation plus EOD snapshots), never a hot row. |
| A5 | M | **EOD blocks a 24/7 digital channel.** Classic CBS "close the day, nobody transacts" does not work for mobile money and apps. | Roll the business date **first** (new postings carry the next date), then batch-process the closed date. EOD steps are idempotent and checkpointed per tenant ([03-domain-model.md §Business date](03-domain-model.md)). |
| A6 | M | **Distributed locks in Redis** for EOD and jobs. Redis locks (incl. Redlock) are unsafe for correctness-critical mutual exclusion. | Use **PostgreSQL advisory locks / ShedLock-JDBC** for jobs. Redis is used for rate limiting, caching, OTP state and other things that are safe to lose. |
| A7 | M | **Notifications and integrations inside the money transaction.** A slow SMS gateway delays or rolls back postings. | **Transactional outbox**: events are written in the same DB transaction as the posting and dispatched asynchronously. External payment calls happen *outside* DB transactions (spec §79), with PENDING state and reconciliation. |
| A8 | M | **Product changes rewrite existing contracts.** Editing a loan product's rate must not change the rate on loans already disbursed. | **Product versioning**: contractual terms are *snapshotted* onto the account / loan at origination. Products are edited by publishing a new version. |
| A9 | M | **Separate `tenant` and `institution` modules** describe the same thing (1:1). | One `tenant` module. `tenant` = the platform's registry record (status, licence, features). `institution_profile` = the tenant's own branding and contact data. |
| A10 | M | **Separate `role`, `permission`, `auth`, `security` modules** create cycles: auth needs roles, staff needs auth, roles need staff. | `iam` (credentials, roles, permissions, sessions, login flows, security config) and `staff` (HR profile, branch scope). `staff → iam` only; `iam` reads staff data through a small SPI (`StaffDirectory`) that `staff` implements. |
| A11 | L | Spec asks for Freezed, Riverpod, GoRouter, Dio, TanStack, shadcn and more. | Accepted. Pinned in the architecture doc, with a rule that new libraries need a reason. |

## 3. Financial-correctness risks

| # | Sev | Risk | Adopted mitigation |
|---|-----|------|--------------------|
| F1 | H | **`double` money in the clients.** The spec bans `float/double` in Java, but **JavaScript `number` and Dart `double` are IEEE-754 floats.** A CMS or Flutter app that parses `"0.1"` into a number re-introduces the bug. | Money is serialized as a **decimal string** (`"amount": "1250.00"`) with an explicit `currency`. Clients use `decimal.js` / Dart `decimal` for display and input. Clients never compute authoritative amounts (spec rule 2). |
| F2 | H | **Unbalanced multi-branch postings.** A customer of Branch A deposits at Branch B: entries balance overall but not per branch, so branch balance sheets don't balance. | The Posting Engine auto-generates **inter-branch (due-to / due-from) entries** so every journal balances **per branch and per currency**. |
| F3 | H | **Double spend under concurrency** (spec §55). | Lock the account balance rows with `SELECT … FOR UPDATE` **in deterministic id order** (avoids deadlocks), check the available balance under the lock, post, and update the projection, all in one DB transaction. Plus a DB `CHECK` that available balance can't go negative for accounts that don't allow overdraft. Concurrency tests are mandatory. |
| F4 | H | **External payment "unknown outcome".** A MoMo/bank request times out: did money leave? Marking it FAILED and releasing funds can pay out twice. | A third outcome, **`PENDING_CONFIRMATION` (unknown)**: funds stay **held**, the status is resolved only by provider callback, status query or reconciliation, and it is never auto-failed on timeout. |
| F5 | H | **Idempotency only in Redis.** Redis eviction or failover forgets a key and a retried transfer posts twice. | Idempotency records live in **PostgreSQL**, inserted in the *same* DB transaction as the money movement (`UNIQUE (tenant_id, scope, key)`). A second, business-level unique constraint covers `financial_transaction (tenant_id, idempotency_key)` and provider references. A replay with a different payload is rejected (`IDEMPOTENCY_KEY_REUSED`). |
| F6 | M | **Interest/rounding drift.** Daily accruals rounded to 2 dp lose or create money over a year; schedule installments don't sum to principal. | Accruals at high precision (`NUMERIC(24,10)`), posting rounded to currency minor units with a documented mode (`HALF_EVEN` default, configurable per product). The rounding remainder carries into the **last installment**. Every method gets property-style tests (Σ principal = disbursed). |
| F7 | M | **Loan interest methods are ambiguous.** "Reducing balance", "amortized" and "simple interest" overlap. | Precise methods: `FLAT`, `DECLINING_BALANCE_EQUAL_INSTALLMENT` (annuity), `DECLINING_BALANCE_EQUAL_PRINCIPAL`; day-count conventions `ACTUAL_365F`, `ACTUAL_360`, `THIRTY_360`. "Custom calculation" is **parameterized strategies, not user-entered formulas** (a formula engine is an injection and audit risk). |
| F8 | M | **No repayment allocation order.** The spec doesn't say whether a partial payment pays penalty, fees, interest or principal first. This changes income recognition. | Configurable **allocation waterfall** per loan product (default: penalties → fees → interest → principal). |
| F9 | M | **Interest on non-performing loans** keeps being recognized as income. | Non-accrual status per classification band. Interest on NPLs goes to a memorandum/suspense account, not income. |
| F10 | M | **Missing loan-loss provisioning.** Delinquency bands exist but no provisioning journals. | Provisioning rules per band (configurable %) generate provisioning journals at EOD/month-end. IFRS 9 ECL staging is a later extension. |
| F11 | M | **No accounting period close.** No year-end closing of income/expense to retained earnings, and back-dated postings into closed periods are possible. | `accounting_period` (OPEN / CLOSED). Postings into a closed period are rejected and corrections post into the current period. Year-end closing journal. |
| F12 | M | **Fees pushing accounts negative.** Monthly fees on a zero-balance account. | Per-fee policy: `SKIP`, `PARTIAL`, `ALLOW_OVERDRAW` (into a receivable), or `ACCRUE_AS_ARREARS`. |
| F13 | M | **Offline collections: officer fraud.** The officer takes cash and records nothing, records less, or records fake collections. | Officer cash is a **sub-ledger** ("cash with collector") that rises on every collection and falls only on deposit to the teller. Customers get an SMS/push receipt **after server posting**. Device receipts carry a per-device monotonic sequence so gaps are detectable. Offline amount/time caps per officer. |
| F14 | L | Susu commission practice (e.g. a collector keeping one day's contribution per cycle) is not modelled. | Fee type `CYCLE_COMMISSION` (N contributions per cycle), configurable per product. |

## 4. Security risks

| # | Sev | Risk | Adopted mitigation |
|---|-----|------|--------------------|
| S1 | H | **Transaction PIN keyspace.** A 4–6 digit PIN has ≤ 10⁶ values; any hash, even Argon2id, is brute-forced offline in minutes if the DB leaks. | PIN hash = Argon2id over **HMAC(pepper, PIN)** where the pepper is held outside the DB (KMS/HSM/secret manager). Strict online retry limits and lockout. |
| S2 | H | **SMS OTP and SIM-swap fraud** (prevalent in mobile-money markets). | SMS OTP is not the strongest factor. Step-up prefers a **device-bound key pair** (private key in Secure Enclave/Keystore, unlocked by biometrics, server challenge–response signing). Integrate SIM-swap checks with telcos where available. New-device + high-value triggers a cooldown. |
| S3 | H | **"Biometric login" implemented as stored password.** | Biometrics only **unlock a device key**. The password and refresh token are never stored behind a biometric prompt. |
| S4 | H | **JWT can't be revoked.** | Short-lived access tokens (10 min) carrying a session id (`sid`), checked against the session store on every request. Rotating refresh tokens with **reuse detection** (a replayed old token revokes the whole session family). |
| S5 | H | **CMS tokens in browser storage** (XSS steals them). | Next.js acts as a **BFF**: tokens live in `HttpOnly; Secure; SameSite=Strict` cookies on the Next.js server and are forwarded server-side. The browser never sees a bearer token. CSRF protection on the BFF. |
| S6 | H | **Super Admin reads tenant financial data** (spec §61). | Platform users are a **separate identity store and token audience**. Platform endpoints run with *no* tenant context, so RLS returns nothing. Cross-tenant support access needs **break-glass**: ticket reference, second approver, time-boxed, fully audited. |
| S7 | H | **Privilege escalation via role management.** An admin grants themselves loan approval. | Users can't change their own roles or status. Platform-scope permissions can't be granted to tenant roles. Role assignment and role edits move under **maker-checker** when the approval engine lands (Phase 2). The default `INSTITUTION_ADMIN` role has **no money-movement permissions** (segregation of duties). |
| S8 | M | **PII at rest** (ID numbers, DOB, photos, signatures). | Field-level encryption (AES-GCM, envelope keys, key version stored) for national ID numbers and similar. **Blind index** (HMAC) for equality lookups and uniqueness. Documents in object storage with SSE plus malware scanning. |
| S9 | M | **Audit logs can be edited.** | The runtime DB role has `INSERT`/`SELECT` only on `audit_log`, plus a trigger that rejects `UPDATE`/`DELETE`. Phase 3 added hash-chain sealing: signed hourly Merkle seals that reveal any later change, even by a database superuser ([Phase 3 blueprint §7](07-phase3-blueprint.md#7-audit-seals)). Export of the seals to WORM storage is still to do. |
| S10 | M | **Security events lost on rollback.** A failed login throws and rolls back, and the audit row vanishes with it. | Business audit writes join the business transaction (an audit row exists only if the change committed). **Security events** (failed login, access denied) are written in a separate `REQUIRES_NEW` transaction. |
| S11 | M | **User enumeration and timing attacks** on login. | Generic `INVALID_CREDENTIALS`. A dummy hash is verified for unknown users so response timing is equal. Rate limiting per IP and per username. |
| S12 | M | **Mobile secrets / certificate pinning.** | No secrets in apps (spec). Pinning is only with a pin-rotation plan (backup pins), because a botched pin rotation bricks every installed app. |

## 5. Scalability risks

| # | Sev | Risk | Mitigation |
|---|-----|------|-----------|
| P1 | M | `ledger_entry` and `audit_log` grow without bound. | Every row carries `business_date` / `occurred_at`. Tables are designed for **monthly range partitioning** (no FKs *into* them, natural partition keys present). Partitioning is switched on when volume warrants. |
| P2 | M | Reports run on the OLTP primary. | Read replica for reports and exports. Reporting queries are isolated in the `reporting` module. A warehouse or OpenSearch comes later. |
| P3 | M | One very large tenant degrades the others (noisy neighbour). | Per-tenant rate limits and job concurrency limits. The schema is tenant-keyed, so a large tenant can later move to its **own database cell** without a model change. |
| P4 | L | High-volume internal accounts (MoMo settlement) as hot rows. | Accounts flagged `balance_check = false` skip synchronous balance locking and are reconciled asynchronously. |

## 6. Missing modules and entities

These are added to the domain model ([03-domain-model.md](03-domain-model.md)) and roadmap:

| Missing item | Why it matters | Phase |
|---|---|---|
| **Holiday calendar** | Due dates, maturities and business-date roll must skip non-working days. | 3 |
| **Currency & minor units** | Rounding scale per currency, multi-currency readiness. | 2 |
| **Charges & taxes engine** (`charge_definition`, `charge_rule`) | Spec mentions fees everywhere but has no module. Levies/withholding taxes must be configurable, not hard-coded. | 2 |
| **Interest accrual** (`interest_accrual`) | Daily accrual for savings and loans, separate from posting. | 2/5 |
| **Inter-branch accounting** | See F2. | 2 |
| **Suspense / unidentified receipts** | MoMo deposits with an unknown or invalid account reference need a home. | 7 |
| **Accounting periods / year-end close** | See F11. | 2 |
| **Loan provisioning** | See F10. | 5 |
| **Repayment allocation waterfall** | See F8. | 5 |
| **Customer groups & group membership** | Group lending and group accounts are in scope, but no `group` entity exists. | 4/5 |
| **Business (legal-entity) customers, signatories, beneficial owners** | AML/CDD needs UBOs. Joint and business accounts need **mandates** ("any one to sign", "two of three"). | 1B/2 |
| **Number sequences** (`number_sequence`) | Customer numbers and account numbers with check digits, gap-free per tenant. | 1B/2 |
| **Limits engine** (`limit_profile`) | Per KYC tier / channel / customer daily limits. | 2 |
| **Standing orders / scheduled transfers** | Target-savings auto-debit, loan auto-repayment from savings. | 6 |
| **Statements** (`statement`) | Listed in Phase 2 but no module or table. | 2 |
| **Transactional outbox** (`outbox_event`) | See A7. | 2 |
| **Document storage** (object storage, `stored_document`) | KYC documents, collateral photos, signatures. Needs S3-compatible storage, encryption and scanning. | 1B |
| **Consent & data-subject requests** | Data-protection law (e.g. Ghana's Data Protection Act, 2012 (Act 843)) needs consent records and access/erasure handling within retention limits. | 6/8 |
| **Complaints / disputes** | Regulators generally expect a complaints register. Transaction disputes feed reversals. | 8 |
| **API clients for integration partners** (`api_client`) | Webhooks and partner integrations need credentials separate from staff users. | 7 |
| **Dormancy & unclaimed balances** | Dormant-account reactivation controls; unclaimed-funds handling per local rules. | 3 |
| **Notification templates & preferences** | Per-tenant templates, opt-outs, language. | 2 |

## 7. Requirements adjusted

| Spec item | Adjustment | Reason |
|---|---|---|
| §2 module list | Merged as described in A9 and A10. `security/` becomes `iam/security`. | Avoid cycles and 1:1 duplication. |
| §2 per-module layout (`controller/ service/ repository/ entity/ dto/ mapper/ …`) | Kept. Added the rule that other modules may only use `service` (public API) and `event`. | Enforceable with ArchUnit. |
| §15 statuses | Added `PENDING_CONFIRMATION` (external outcome unknown, funds held). `SUCCESS` is defined strictly as "ledger journal committed". | See F4. |
| §17 `LedgerAccount` / `ChartOfAccount` | `chart_of_account` = the GL. `ledger_account` = sub-ledger posting account (customer deposit, loan, teller drawer, vault, collector cash, settlement). Every `ledger_entry` has a GL account and an optional ledger account. | GL and sub-ledger reconcile by construction. |
| §49 `user` table | Split into staff credentials, platform users, and (Phase 6) customer credentials. | Different policies; a customer row can never carry staff permissions. |
| §49 `transaction_entry` | Dropped (see A3). | One source of truth. |
| §49 `institution` | Merged into `tenant` + `institution_profile`. | See A9. |
| §56 "distributed locks" | PostgreSQL advisory locks / ShedLock. | See A6. |
| §1 Java version | Java **21 LTS** now, moving to Java 25 LTS is a one-line change. | 21 builds on the current dev machines (JDK 22) without extra installs. All chosen libraries support both. |
| §3 Redis | Redis-protocol compatible. **Valkey** (BSD-licensed fork) is a drop-in if Redis licensing is a concern for commercial redistribution. | Licensing. |
| §46 "Keycloak later" | Tokens are standard asymmetric JWTs (RS256, `kid`, JWKS-ready), so moving staff identity to Keycloak/an IdP later doesn't change resource servers. | Migration path. |
| §21/§29 hard-coded labels | Confirmed: susu frequencies, delinquency bands, ID document types and KYC tiers are **tenant configuration**, not enums. | Spec rule 18. |

## 8. Regulatory notes (not legal advice)

The platform **does not claim regulatory compliance**. Before go-live with a licensed institution, qualified counsel and the institution's compliance function must review at least the following (Ghana as first market):

- The Bank of Ghana licensing tier of the client institution (it determines permitted products) and applicable BoG directives on cyber and information security, outsourcing/cloud, and data residency.
- *Banks and Specialised Deposit-Taking Institutions Act, 2016 (Act 930)*, *Payment Systems and Services Act, 2019 (Act 987)*, *Anti-Money Laundering Act, 2020 (Act 1044)*, *Data Protection Act, 2012 (Act 843)*, and any rules on susu collection and agent operations.
- Identity verification through authorized channels only (NIA / Ghana Card via an approved provider).
- Prudential loan classification and provisioning percentages, which must come from current regulation and be loaded as **configuration**.

Laws are referenced so the design leaves room for them. All thresholds, retention periods and classification rules are **configurable data**.

## 9. Decisions log (summary)

| ID | Decision |
|---|---|
| D1 | Modular monolith, one deployable, two runtime roles (`api`, `worker`). |
| D2 | Shared database, shared schema, `tenant_id` on every tenant row, PostgreSQL RLS, composite tenant FKs. |
| D3 | Double-entry ledger is the only source of truth; Posting Engine is the only writer. |
| D4 | Money = `BigDecimal` / `NUMERIC(19,4)`, decimal **strings** on the wire. |
| D5 | Idempotency persisted in PostgreSQL, same transaction as the effect. |
| D6 | Transactional outbox for events; no external I/O inside money transactions. |
| D7 | Staff, platform and customer identities are separate stores and token audiences. |
| D8 | Next.js is a BFF; it never touches the database and never exposes tokens to the browser. |
| D9 | Contract terms are snapshotted from versioned products. |
| D10 | Jobs use PostgreSQL advisory locks; Redis only for safe-to-lose state. |
