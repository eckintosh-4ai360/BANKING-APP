# 07 — Phase 3 Blueprint: Branch Operations

Phase 3 turns the banking core into something a branch can run day after day: a stored business date and calendar, tills and vaults that are real ledger accounts, end-of-day processing that can stop anywhere and resume, deposit interest, dormancy, cash reconciliation and a sealed audit trail. The order follows the [roadmap](04-roadmap.md#phase-3-branch-operations). Everything is in `backend/banking-core`; the CMS screens are listed in §10.

## 1. Modules and migrations

| Module | Responsibility |
|---|---|
| `ledger` (`BusinessDateService`) | The institution's business date: stored, carried by every posting, moved only by end-of-day. |
| `operations` | Business calendar (working week, holidays), end-of-day runs, daily GL snapshot. |
| `common.eod` | The end-of-day SPI: `EndOfDayStep` (code, order, idempotent `run`), `EndOfDayCheck` (must pass before a date closes), `EndOfDayContext` (closed date, next date, transactions, checkpoints), `EndOfDayProbe` (test hook). |
| `teller` | Vaults, drawers, teller sessions and cash counts, cash movements and cash in transit, end-of-day cash reconciliation. |
| `account` | Deposit interest (accrual and payout) and dormancy, as end-of-day steps. |
| `audit` | Audit seals: hash chain, Merkle roots, signatures, verification. |

| Migration | Contents |
|---|---|
| `V18__business_calendar.sql` | `business_day` (per institution), `business_calendar` (working week), `holiday`; `operations.*` permissions |
| `V19__end_of_day.sql` | `eod_run`, `eod_step` (status, attempts, result, checkpoints), `gl_balance_snapshot` |
| `V20__teller.sql` | `vault`, `cash_drawer`, `teller_session` (one open session per drawer and per teller, four eyes on differences), `cash_count` (immutable), `cash_movement` (four eyes), `financial_transaction.cash_drawer_id` |
| `V21__interest_and_dormancy.sql` | `deposit_interest_position`, `deposit_interest_accrual` (immutable except its journal link), `deposit_interest_payout` (immutable), `account.last_activity_on` |
| `V22__audit_seal.sql` | `audit_seal` (chain enforced by trigger, append-only) and the audit insert guard |
| `V23__cash_position.sql` | `cash_position` (daily, immutable) |

## 2. Business date and calendar

- The business date is stored per institution (`business_day`) and only end-of-day moves it, to the next working day of the institution's calendar (working week minus holidays). A day's books stay that day's books however late the branches close.
- Every posting takes a **share lock** on the institution's `business_day` row (`forPosting()`); the end-of-day roll takes it **exclusively**. The roll therefore waits for postings in flight, and every later posting carries the new date: end-of-day never misses a journal of the date it closes.
- End-of-day journals (interest) post with `postForClosedDate`, which only accepts the date that was just rolled from.
- Holidays can only be added or removed in the future; changing the working week is versioned (optimistic lock).

## 3. End-of-day

1. **Checks.** Every `EndOfDayCheck` must pass. The teller module refuses while any till is `OPEN` or `BALANCING`.
2. **Roll.** In one transaction the run and its steps are recorded and the business date moves to the next working day, so branches carry on while the closed date is processed.
3. **Steps.** Run in order for the closed date, each committing its own batches and recording checkpoints. A failure or a crash stops the run (`FAILED`); **resume** re-runs the unfinished steps, which skip what they already did. One worker per institution at a time (cluster lock); steps run as the system actor. Runs execute in the background (`banking.eod.async`, inline in tests).

| Order | Step | Does | Checkpoints |
|---|---|---|---|
| 100 | `DEPOSIT_INTEREST_ACCRUAL` | Accrues each calendar day up to the next business date; posts the GL increments | `batch`, `journal` |
| 200 | `DEPOSIT_INTEREST_PAYOUT` | Pays every interest period that ended in those days | `batch` |
| 300 | `DORMANCY` | Marks idle accounts dormant | `batch` |
| 400 | `CASH_RECONCILIATION` | Records every vault's and drawer's cash position | `written` |
| 800 | `GL_SNAPSHOT` | Closing position of every GL account, branch and currency | `before`, `written` |
| 900 | `LEDGER_RECONCILIATION` | Every balance equals its entries, every journal balances; a break fails the run | `before` |

Every step is keyed by what it produces ((account, day), (account, period end), (date, cash point), (date, GL, branch, currency)), so running it twice cannot do anything twice.

## 4. Tellers, vaults and cash

- **Vaults and drawers are balance-checked ledger accounts** (under vault cash and cash at branch). Their cash *is* their ledger balance and the database refuses to take it below zero. One active vault per branch and currency.
- **Teller session.** A teller opens a till on a free drawer (with an optional opening count that must match the ledger). Cash deposits and withdrawals post to the drawer of the teller's open session, which the transaction holds share-locked; closing locks it exclusively, so no posting slips past a count. Reversing a cash transaction needs the drawer's till open.
- **Closing count.** The teller enters pieces per denomination; the server computes the total and compares it with the drawer's ledger balance only after the count is submitted. Equal: `CLOSED`. Different: `BALANCING` until a supervisor (never the teller; also a database check) accepts it: a shortage is posted to *teller shortages*, an overage to *other income*, and the drawer then matches the count (`CLOSED_WITH_DIFFERENCE`).
- **Cash movements** (`VAULT_TO_DRAWER`, `DRAWER_TO_VAULT`, `BANK_TO_VAULT`, `VAULT_TO_BANK`, `VAULT_TO_VAULT`): one person requests, another approves (four eyes, database check), and the approval posts it. Between branches the cash sits in *cash in transit* until the receiving branch confirms receipt.
- **Cash reconciliation** (`CASH_RECONCILIATION`): for the closed date, each drawer's ledger balance is compared with its last closing count. They must be equal, because nothing posts to a drawer without an open session; a difference is a `BREAK`, audited (`CASH_RECONCILIATION_BREAK`) and shown on the Cash screen. A break does not stop end-of-day: the books still balance, the cash does not, and that is for people to investigate. Vault counts are not recorded yet, so vaults show `NOT_COUNTED`.

## 5. Deposit interest

| Rule | Implementation |
|---|---|
| Daily interest | Closing balance × rate × day weight ÷ (100 × year days), exact to 8 decimals, half-even (`InterestCalculator`). Nothing on a zero or negative balance. |
| Day counts | `ACTUAL_365F`, `ACTUAL_360`, `THIRTY_360` (the 31st earns nothing, the end of February earns the missing days). |
| Weekends and holidays | Accrued by the preceding business date's end-of-day on its closing balance. |
| GL | Daily-balance methods post the change of the **rounded cumulative total** (Dr interest expense, Cr interest payable). An account's share of interest payable is always `round(accrued) − paid`: no drift, ever. |
| Payout | At each period end (`MONTHLY`, `QUARTERLY`, `ANNUALLY`) the account is credited `round(accrued to the period end) − paid` from interest payable. Sub-unit remainders stay in the cumulative total and are paid once they add up; nothing is rounded twice. |
| After a period end in the same run | Days after a month end closed by the same end-of-day (a month ending on a Friday) accrue on the balance **including** the interest credited at the period end. Both steps compute the payout with the same function, so they agree. |
| Minimum balance (`MIN_MONTHLY_BALANCE`) | Interest on the period's lowest closing balance, paid straight from interest expense at the period end. |

Example (from `DepositInterestIT`): 10,000.00 at 3.65% on actual/365 earns 1.00 a day; April 27–30 pays 4.00 on the 30th; May 1–2 then earn 1.0004 a day on 10,004.00. A 1,000.00 minimum-balance account that dropped to 600.00 earns 600 × 12% × 4 ÷ 365 = 0.78904110, paid as 0.79.

## 6. Dormancy

An `ACTIVE` account becomes `DORMANT` when its last customer activity is the product's dormancy period (30–3,650 days, default 365) before the closed date. Activity is recorded as the **business date** of the last customer posting (`account.last_activity_on`), written in the posting's transaction; interest and charges don't count. Dormant accounts accept money but pay nothing out until reactivated. Each change is audited (`ACCOUNT_DORMANT`).

## 7. Audit seals

Spec risk S9 (audit logs can be edited) was mitigated in Phase 1 by an insert-only role and triggers. Phase 3 adds tamper evidence that holds even against someone with full database access.

- **Seal.** Each trail (each institution, and the platform's own events) is sealed period by period (an hour by default, `lag` after it ends). A seal stores the row count and Merkle root of the period's rows, the previous seal's hash and an HMAC-SHA256 signature with a server-held key (`banking.audit.seal.keys`, versioned for rotation). The hashes are defined in `AuditHashing` precisely enough to verify a seal without this code: rows are encoded field by field with lengths, and leaf and node hashes use different prefixes.
- **Nothing slips in or out.** Every audit insert holds its trail's seal lock (shared) until it commits, and the sealer takes it exclusively, so a seal never misses a row still being written. The database refuses audit rows dated inside a sealed period. Seals are append-only and must continue their chain (database trigger).
- **Verification.** `GET /audit-seals/verification?from&to` (up to 31 days) recomputes every seal in the period from the rows as they are now and reports `ROWS_CHANGED`, `SEAL_ALTERED`, `CHAIN_BROKEN`, `SIGNATURE_INVALID` or `KEY_UNAVAILABLE`. Someone who rewrites rows and recomputes the seal without the key is caught by the signature and the next seal's link. Each check is itself audited.
- **Scheduling.** `MaintenanceJobs.sealAuditTrails` every `interval` (10 minutes), at most `max-per-run` seals per trail per run, so catching up after downtime is spread out.

## 8. API (all under `/api/v1`)

| Endpoint | Permission | Notes |
|---|---|---|
| `GET /operations/business-date` | authenticated | current, previous and next business date, working week |
| `PUT /operations/working-week` | `operations.manage` | versioned |
| `GET·POST /operations/holidays`, `DELETE /operations/holidays/{date}` | `operations.view` / `operations.manage` | future dates only |
| `POST /operations/eod` · `/{id}/resume`, `GET /operations/eod[/{id}]` | `operations.manage` / `operations.view` | 202 with the run |
| `GET·POST /cash/vaults`, `GET·POST /cash/drawers` | `cash.view` / `cash.manage` (drawer list also `teller.operate`) | branch scope applies |
| `GET·POST /cash/movements`, `POST /cash/movements/{id}/approve·receive·reject·cancel` | `cash.manage`, `teller.operate` (request, cancel) | four eyes |
| `GET /cash/positions?date&branchId` | `cash.view`, `cash.manage`, `teller.supervise` | latest closed date by default |
| `POST /teller/sessions`, `GET /teller/sessions/me`, `POST /teller/sessions/{id}/close` | `teller.operate` | |
| `GET /teller/sessions?date`, `GET /teller/sessions/{id}` | `teller.supervise`, `cash.view` (own sessions with `teller.operate`) | |
| `POST /teller/sessions/{id}/accept-difference` | `teller.supervise` | never the teller |
| `GET /audit-seals`, `GET /audit-seals/verification` | `audit.view` | institution trail |
| `GET /platform/audit-seals`, `GET /platform/audit-seals/verification` | `platform.audit.view` | platform trail |

Cash deposits and withdrawals no longer take a `branchId`: they post to the caller's open till (`TELLER_SESSION_REQUIRED` otherwise).

## 9. New error codes and configuration

Operations: `HOLIDAY_NOT_IN_FUTURE`, `HOLIDAY_EXISTS`, `NO_BUSINESS_DAY_AHEAD`, `EOD_ALREADY_RUNNING`, `EOD_CHECKS_FAILED`, `EOD_STEP_FAILED`, `EOD_NOTHING_TO_RESUME`. Teller: `VAULT_EXISTS`, `NO_VAULT`, `DRAWER_EXISTS`, `DRAWER_NOT_AVAILABLE`, `DRAWER_IN_USE`, `SESSION_ALREADY_OPEN`, `TELLER_SESSION_REQUIRED`, `SESSION_NOT_OPEN`, `SESSION_NOT_BALANCING`, `NOT_YOUR_SESSION`, `OPENING_COUNT_MISMATCH`, `INVALID_DENOMINATIONS`, `CASH_INSUFFICIENT`, `CURRENCY_MISMATCH`, `MOVEMENT_NOT_PENDING`, `INVALID_MOVEMENT`. Removed: `CASH_BRANCH_NOT_AVAILABLE`.

| Key | Default | Meaning |
|---|---|---|
| `banking.eod.async` | `true` | run end-of-day steps in the background after the roll (tests: `false`) |
| `banking.audit.seal.active-key-version` / `keys.<n>` | `AUDIT_SEAL_KEY_ACTIVE_VERSION`, `AUDIT_SEAL_KEY_V1` | HMAC keys (≥ 256 bits, base64); the application refuses to start without one, and deployed environments refuse the published development keys |
| `banking.audit.seal.period` / `lag` | `PT1H` / `PT5M` | seal length (whole seconds, aligned to the epoch) and how long after its end a period is sealed |
| `banking.audit.seal.interval` / `max-per-run` | `PT10M` / `168` | sealing job delay and catch-up limit |

## 10. CMS screens

- **My till** (`/teller`, `teller.operate`): open a till on a free drawer with an optional opening count, see the drawer's cash, close with a note-by-note count (the total shown is computed exactly in integer arithmetic and never sent; the server computes its own), see a difference waiting for a supervisor.
- **Cash** (`/cash`): vaults and drawers (create with `cash.manage`), cash movements (request, approve, reject, receive, cancel; the screen hides approve from the requester and the server enforces it), teller sessions of a date with supervisor review of differences, and end-of-day cash positions with breaks highlighted.
- **End of day** (`/operations`): business, next and last closed dates; run end-of-day (confirmation), runs with their steps, results and errors, resume of a failed run, working week and holidays.
- **Audit log**: an *Integrity seals* tab in the CMS (institution trail) and in Super Admin (platform trail), listing seals and verifying a period.

## 11. Exit gate results

| Gate | Evidence |
|---|---|
| End-of-day killed at every step and re-run produces identical results | `EndOfDayIT.aRunStoppedAtAnyCheckpointResumesToTheResultOfAnUninterruptedRun`: a scenario at a Friday month end (two branches, an inter-branch transfer, daily-balance and minimum-balance accounts, a vault and a drawer) is stopped, as a crash would, at each of the 8 checkpoints of §3 and resumed. Each time the GL snapshot, the interest accruals, payouts and positions, and the cash positions equal those of an uninterrupted run, and a second run is refused while one is unfinished. |
| A teller's expected balance always equals the ledger | Drawers are balance-checked ledger accounts, so the expected cash *is* the ledger balance (`TellerIT.aBalancedDayClosesCleanAndTheExpectedCashIsAlwaysTheLedger` checks it after every one of a random series of deposits and withdrawals). End-of-day compares every drawer with its last count and flags any posting after the count (`TellerIT.endOfDayReconcilesEveryDrawerAndVaultAndFlagsCashPostedAfterTheCount`). |
| Interest is exact and the GL never drifts | `InterestCalculatorTest` (day counts, rounding, no drift over a year), `DepositInterestIT` (amounts to the cent and the 8th decimal, GL interest payable equal to accrued minus paid, weekend after the month end, minimum balance). |
| Tampering with the audit trail is found | `AuditSealIT`: rows rewritten or deleted as the database superuser with triggers off, and a seal recomputed without the key, are all reported; nothing can be added to a sealed period; a seal waits for an audit row still being written. `AuditHashingTest` pins the hash definitions. |
| Whole build | 122 backend unit tests and 167 integration tests (real PostgreSQL 18), 142 web tests, both Next.js apps build. |

## 12. Known follow-ups

- **Closing an account with accrued interest.** Closing requires a zero balance; interest accrued since the last period end stays in interest payable. A closing payout belongs in the account-closure flow.
- **Withholding tax on interest** is not applied. The applicable rules must be reviewed before it is built; nothing here claims tax or regulatory compliance.
- **Year-end closing journal** (income and expense to retained earnings).
- **Vault counts** under dual control, so vaults can be `MATCHED` like drawers; reconciliation of cash in transit as of a date.
- **Interest in the transaction history.** Interest credits are journals posted by end-of-day: they appear in statements (built from the ledger) but not in the account's transaction list.
- **Audit seal export.** A copy of each seal hash outside the database (WORM storage) would also catch someone deleting the newest seals together with their rows.
- **Till cash shown to the teller.** The till screen shows the drawer's cash position. The comparison happens only when the count is submitted, but this is not a strictly blind count; hiding the expected cash from tellers is an institution policy option still to add.
- **Account branch transfers** would need interest payable moved with the account; there is no branch transfer flow yet.
