# 09 — Phase 5 Blueprint: Loans

Phase 5 adds lending: versioned loan products, applications through a workflow where no one decides alone, disbursement and repayment through the ledger, interest recognised as it is earned, end-of-day arrears handling (penalties, days past due, delinquency bands, non-accrual and provisioning), and collections, restructure and write-off. The order follows the [roadmap](04-roadmap.md#phase-5-loans). The backend module is `loan` in `backend/banking-core`; the screens are in `web/institution-cms`.

## 1. Modules and migrations

| Package | Responsibility |
|---|---|
| `loan.schedule` | Pure arithmetic, no Spring: `ScheduleCalculator`, `InterestEarned`, `RepaymentAllocator`, `LoanArrears`, plus the interest methods, frequencies and day counts. |
| `loan.service` | Products and their versions, applications and their workflow, disbursement, repayment, accrual, the `LOAN_PORTFOLIO` end-of-day step, collections, restructure, write-off and recovery. |
| `transaction` | Two new transaction types, `LOAN_DISBURSEMENT` and `LOAN_REPAYMENT`. The loan module builds the loan's side of the journal; `TransactionService.postLoanMovement` adds the customer's side with the usual checks. Loan transactions cannot be reversed through the generic reversal (`REVERSAL_NOT_SUPPORTED`), because the schedule records what each one settled. |
| `approval` | Two new approval types, `LOAN_RESTRUCTURE` and `LOAN_WRITE_OFF`, run by handlers in the loan module. |

| Migration | Contents |
|---|---|
| `V26__loans.sql` | `loan_product`, `loan_product_version` (published terms never change, by trigger; one published version per product), `loan_application`, `loan_application_step` (immutable; separation of duties enforced by unique indexes and a trigger), `loan_guarantor`, `loan_collateral`, `loan`, `loan_installment` (versioned schedule), `loan_repayment` (immutable; the split must add up to the amount); `loan.repay` permission |
| `V27__loan_portfolio.sql` | `loan_delinquency_band` (institution configuration); provision held, penalty and end-of-day progress on `loan`; exact penalty on `loan_installment` |
| `V28__loan_collections.sql` | `loan_collection_activity`, `loan_restructure` and `loan_recovery` (both immutable); written-off and recovered amounts, band floor and schedule start on `loan`; carried interest on `loan_installment`; the two approval types; `loan.collect` and `loan.restructure` permissions |

New chart of accounts entries (added to existing institutions at start-up): **1230** Penalty receivable, **1295** Interest in suspense (credit-normal), **4230** Loan fees, **4240** Loan penalties, **4250** Recoveries of written-off loans.

## 2. Products and schedules

A product's terms live in versions: amount and installment limits, interest method, annual rate, day count, repayment frequency, principal and interest grace, rounding, the repayment allocation order, processing fee (rate and flat), penalty rate and grace days, guarantors required, collateral cover, the second-approval threshold, the KYC tier required, and its GL accounts (defaulting to the system accounts, checked for class and normal side). A draft is edited, then published; publishing retires the previous version. Applications use the published version; a loan keeps a copy of its terms.

`ScheduleCalculator` supports **flat**, **declining balance with equal installments** and **declining balance with equal principal**; **daily, weekly, biweekly, monthly and quarterly** repayments; **actual/365, actual/360 and 30/360**. It works in `BigDecimal` at 34 significant digits and rounds only where money falls due:

- each period's rate is the annual rate times that period's year fraction, so an irregular first period is charged exactly;
- interest is rounded **cumulatively** (each installment takes the change in the rounded running total), so the total never drifts;
- principal is split by rounding down, with the remainder in the last installment; the equal installment is `A = P / Σₖ Πⱼ≤ₖ (1 + iⱼ)⁻¹`, and the last installment repays whatever is left. **Principal always adds up to exactly the amount disbursed.**

## 3. Applications and separation of duties

`DRAFT → SUBMITTED → ASSESSED → RECOMMENDED → APPROVED → DISBURSED`, or `REJECTED` / `WITHDRAWN` on the way.

- **Eligibility.** The borrower must be an active (KYC-approved) customer at or above the product's KYC tier, checked when the application is made, submitted, approved and disbursed. The money is paid into an active savings or current account the borrower holds, in the loan's currency. The amount and installments must be within the product's limits.
- **Separation of duties.** Whoever recommends, approves, gives a second approval or disburses must be someone other than the loan officer and than everyone who took another of those steps. The service checks it (`SEPARATION_OF_DUTIES`) and the database enforces it (unique index on application and actor for those steps, and a trigger refusing the loan officer).
- **Security.** Guarantors and collateral count only once verified, by someone other than the loan officer. Approval needs the product's number of verified guarantors and verified forced-sale value covering its share of the approved amount.
- **Second approval.** Above the product's threshold an approved application waits for a second approver.
- **Approval terms.** The approver sets the amount (at most what was asked for), the installments and optionally the first due date. At disbursement the first due date must be after today and within two repayment periods (a longer wait is a grace period, which belongs in the product).

## 4. The loan in the ledger

Each loan has three balance-checked, debit-normal ledger accounts of its own: **principal** (under 1210), **interest receivable** (1220) and **penalty receivable** (1230). Its balances are these accounts; the schedule is the contractual plan and the record of what each repayment settled.

| Event | Debit | Credit |
|---|---|---|
| Disbursement | loan principal | borrower's account |
| Processing fee (taken from the disbursement) | borrower's account | 4230 Loan fees |
| Interest recognised | loan interest receivable | 4100 Interest income (1295 while non-accrual) |
| Penalty | loan penalty receivable | 4240 Loan penalties (1295 while non-accrual) |
| Repayment | borrower's account or the teller's drawer | loan principal, interest, penalty receivable |
| Interest or penalty collected while non-accrual | 1295 Interest in suspense | 4100 / 4240 |
| Provision raised (released: the reverse) | 5200 Provision expense | 1290 Allowance for loan losses |

Disbursements and repayments are idempotent on their request key, post in one database transaction with the loan's row lock, and every amount comes from the server: the processing fee from the product, the split from the schedule.

## 5. Interest, repayments and settlement

- **Recognition.** `InterestEarned` spreads each installment's interest evenly by calendar day over its accrual window (from the previous installment that had interest due, so interest deferred by a grace period is earned over the whole deferred stretch). Interest of installments already due is earned in full; the running part is rounded down. Recognition is cumulative: end-of-day and every repayment recognise only the difference between what has been earned and what is recognised, so it never drifts and never happens twice.
- **Repayment.** Interest is first caught up to today. Installments due by today are settled oldest first, each in the product's allocation order (by default penalty, fee, interest, principal); anything left prepays principal of the next installments. Interest of an installment is never collected before it falls due, because it is not earned yet.
- **Settlement.** The payoff amount is all principal, the interest earned so far and penalties; the interest not yet earned is waived on the schedule. Paying exactly the payoff amount closes the loan (after checking its three accounts are at zero, otherwise the repayment rolls back), releases its provision and hands back its collateral. More than the payoff is refused (`OVERPAYMENT`), and so is an amount that would repay all principal while leaving earned interest owing (`SETTLE_WITH_PAYOFF`).

## 6. End-of-day: arrears, non-accrual and provisioning

`LOAN_PORTFOLIO` (order 350, after deposit interest and susu, before the GL snapshot) closes each active loan for the business date in one journal on that date:

1. **Interest** earned through the date.
2. **Penalties**: simple interest at the penalty rate (actual/365) on each installment's overdue principal and interest, for each day after its due date plus the grace days. Kept exact per installment and rounded cumulatively. Days that are not business days are charged when the next business day closes, on the amount overdue then.
3. **Days past due**: days since the due date of the oldest installment still owing anything.
4. **Delinquency band**: the institution's bands (by default Current from 0 days at 1%, Watch from 1 at 5%, Substandard from 31 at 25%, Doubtful from 91 at 50% with accrual suspended, Loss from 181 at 100% with accrual suspended). These are configuration to review against the institution's regulator; nothing here claims compliance with a regulation. Provision rates may not fall, and suspension may not end, as days past due grow.
5. **Non-accrual**: when a loan enters a suspending band, the interest and penalties it owes move out of income into **1295 Interest in suspense**; later accruals go to suspense, collections move from suspense to income, and when the loan is cured what is left moves back. While suspended, suspense always equals the loan's receivables.
6. **Provision**: the band's rate of the principal outstanding, adjusted to that target (raised or released).

A loan done for a date is skipped (`portfolio_processed_through`), so a run stopped after a batch resumes without charging anything twice. Each loan's band change is audited (`LOAN_RECLASSIFIED`). The step also decides promises to pay (§7).

## 7. Collections, restructure, write-off, recovery

- **Collections queue** (`GET /loans/arrears`): active loans at least *n* days past due in the caller's branches, longest first, with what is overdue and the latest contact.
- **Activities**: calls, visits, SMS, letters and **promises to pay** (amount and date within 90 days). On the promised date, end-of-day marks a promise `KEPT` when repayments since it was made add up to the amount, otherwise `BROKEN`.
- **Restructure** (maker `loan.restructure`, checker `approval.act` + `loan.approve`): interest is caught up, then the principal still owed is scheduled again at the loan's rate from today over the new installments. The interest and penalties already owed are **carried** into the new first installment, recognised already and not earned again, so the payoff is the same immediately before and after. The approver chooses how many days the loan **keeps its current band** whatever its new days past due, so a reschedule cannot make a bad loan look current at once.
- **Write-off** (maker `loan.writeoff`, checker `approval.act` + `loan.writeoff`): one journal posted by the maker and approved by the checker. The principal comes off against the provision held (the shortfall is expensed, or an excess released), and uncollected interest and penalties come off against suspense or the income they were recognised in. The loan's accounts end at zero; collateral stays pledged for recovery.
- **Recovery** (`loan.repay`): money recovered on a written-off loan is credited to **4250 Recoveries**, from the borrower's account or in cash, never more than was written off and not yet recovered; idempotent.

## 8. API (all under `/api/v1`)

| Endpoint | Permission | Notes |
|---|---|---|
| `GET /loan-products`, `GET /loan-products/{id}`, `GET /loan-products/{id}/schedule-preview` | `product.view`, `product.manage`, `loan.view` | the preview is computed by the server |
| `POST /loan-products`, `PUT /loan-products/{id}`, `POST /loan-products/{id}/versions`, `PUT /loan-products/{id}/versions/{v}`, `POST …/publish` | `product.manage` | |
| `GET·POST /loan-applications`, `GET·PUT /loan-applications/{id}`, `GET …/schedule-preview` | `loan.view` / `loan.create` | the creator is the loan officer |
| `POST /loan-applications/{id}/submit`, `…/withdraw` | `loan.create` | |
| `POST …/assess` | `loan.assess` | risk rating and note |
| `POST …/recommend` | `loan.recommend` | not by the loan officer |
| `POST …/approve`, `…/second-approval`, `…/reject` | `loan.approve` | |
| `POST …/guarantors`, `…/collateral` | `loan.create` | |
| `POST …/guarantors/{g}/verify`, `…/collateral/{c}/verify` | `loan.assess` | not by the loan officer |
| `POST …/collateral/{c}/release` | `loan.approve` | rejected, withdrawn or repaid only |
| `POST /loan-applications/{id}/disburse` | `loan.disburse` | `Idempotency-Key` required |
| `GET /loans`, `GET /loans/{id}`, `GET /loans/{id}/payoff` | `loan.view` | |
| `POST /loans/{id}/repayments` | `loan.repay` | `Idempotency-Key` required; `ACCOUNT` or `CASH` (the caller's open till) |
| `GET /loans/arrears`, `GET /loans/{id}/collection-activities` | `loan.view` | |
| `POST /loans/{id}/collection-activities` | `loan.collect` | |
| `POST /loans/{id}/restructure`, `POST /loans/{id}/write-off` | `loan.restructure`, `loan.writeoff` | answer `202` with the approval request |
| `POST /loans/{id}/recoveries` | `loan.repay` | `Idempotency-Key` required |
| `GET /loan-portfolio`, `GET·PUT /loan-portfolio/delinquency-bands` | `loan.view` / `product.manage` | PAR 30, provision and non-accrual by currency and band |

Default roles: loan officers create, assess, recommend, collect and ask for restructures; branch managers approve, disburse, take repayments, collect, restructure and write off (four eyes still applies); tellers view loans and take repayments; field officers record collection activity.

## 9. CMS screens

- **Loan applications**: list by status; a new application with the server's schedule preview; the application with its workflow trail, guarantors and collateral (add, verify, release), and the steps the user may take (assess, recommend, approve with terms, second approval, reject, withdraw, disburse).
- **Loans**: list by status; a loan with balances from its ledger accounts, the payoff amount, its schedule, repayments, restructures and recoveries; collection activity; repay (account or cash, with a payoff shortcut), ask to restructure or write off, record recoveries.
- **Collections**: portfolio summary per currency (active loans, principal outstanding, PAR 30, provision, non-accrual) and by band, and the arrears queue.
- **Loan products**: products and their versions (publish a draft, start a new one), a new product form, and the delinquency band editor.
- **Approvals** lists loan restructures and write-offs with the other requests.

## 10. Exit gate results

| Gate | Evidence |
|---|---|
| Σ installment principal = disbursed principal for every method and frequency | `ScheduleCalculatorTest`: 2,025 random schedules (45 for each of the 45 method × frequency × day-count combinations, with random amounts, rates, grace periods and rounding) all repay exactly the principal and end at zero; nothing is negative, amounts are in cents, the balance only goes down, due dates move forward and the grace periods hold. |
| Golden files against independently computed schedules | `ScheduleGoldenTest`: 54 cases covering all 45 combinations (847 installments), computed by `src/test/resources/loan-schedules/generate_golden.py` from the documented definitions in Python's `decimal`, with no code shared, reproduced to the cent. |
| Interest recognition | `InterestEarnedTest`: never decreases, never above the scheduled total, equal to what fell due on each due date, over 200 random schedules day by day. |
| Repayment split and settlement | `RepaymentAllocatorTest`; `LoanIT`: an application through its workflow, idempotent disbursement, a repayment on the due date, a prepayment that never touches unearned interest, refusal of overpayment and of a near-settlement, settlement in cash with the unearned interest waived, the loan's accounts at zero, income and fees in the trial balance, and the generic reversal refused. |
| Separation of duties | `LoanIT`: the loan officer cannot verify guarantors or collateral or recommend their application; the approver cannot disburse or give the second approval; the database refuses a decision by the loan officer inserted around the service. |
| Arrears, non-accrual and provisioning | `LoanPortfolioIT`: day-by-day interest, penalties over a weekend, Watch then Doubtful with interest and penalties moved to suspense (24.74), collection out of suspense, cure back to Current with 4.00 returned to income, provisions 10 → 100 → 500 → 5; a run stopped after the loan batch resumes without charging twice. |
| Collections, restructure, write-off | `LoanCollectionsIT`: the arrears queue, promises kept and broken; a restructure needing a checker that leaves the payoff unchanged and holds the band; a write-off needing a different checker that uses the provision and expenses the rest; recoveries as income, idempotent, never above what was written off. |
| Whole build | 203 backend unit tests and 185 integration tests; 148 web tests, including the lending screens (navigation, a workflow step refused for separation of duties, a payoff taken exactly as quoted with an idempotency key). |

## 11. Known follow-ups

- **Field collections for loans.** The field app and `/field/sync` collect into deposit accounts and susu plans; repaying a loan in the field is not wired yet (the `FIELD` repayment source exists in the schema).
- **Correcting a loan movement.** Loan transactions are refused by the generic reversal; a correction workflow for a mistaken repayment (reverse it and re-open what it settled, with approval) is not built yet.
- **Installment fees.** The allocation order has a `FEE` component, but only the processing fee (taken at disbursement) exists; per-installment fees are not charged.
- **Restructure terms.** A restructure keeps the loan's rate and method; changing them needs a new loan.
- **Promises on written-off loans** are recorded but not decided at end-of-day (only active loans are processed).
- **Loan statements** for customers and the loans view in the customer app arrive with Phase 6; portfolio reporting (PAR 90, disbursement and collection reports, exports) with Phase 9.
- **End-of-day gate.** `EndOfDayIT`'s every-checkpoint stop-and-resume scenario has no loan in it; the loan step's resume is covered by `LoanPortfolioIT`.
