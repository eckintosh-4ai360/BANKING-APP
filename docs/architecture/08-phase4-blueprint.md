# 08 — Phase 4 Blueprint: Microfinance and Field Operations

Phase 4 takes banking into the field: officers collect cash from their customers on a phone that may be offline for hours, susu customers contribute on a schedule, and every cedi an officer carries is accounted for until a teller counts it in. The order follows the [roadmap](04-roadmap.md#phase-4-microfinance). Backend modules are in `backend/banking-core`, the screens in `web/institution-cms`, the app in `mobile/field_officer_app`.

## 1. Modules and migrations

| Module | Responsibility |
|---|---|
| `fieldops` | Field officers and their cash with collectors account, customer assignments, devices, offline sync of collections and visits, field alerts, collector remittance to tellers. |
| `susu` | Susu frequencies (institution configuration), plans, the contribution schedule, cycle commission, the `SUSU_CONTRIBUTIONS` end-of-day step. |
| `transaction` | A new transaction type, `FIELD_COLLECTION` (channel `FIELD`): Dr cash with collectors, Cr the customer's account. |

| Migration | Contents |
|---|---|
| `V24__field_operations.sql` | `field_officer`, `customer_assignment` (one open assignment per customer), `field_device` (one live registration per phone), `collection` (unique client reference per institution, unique sequence number per device, immutable), `customer_visit` (immutable), `field_alert`, `collector_remittance` (teller ≠ officer, immutable); `field.manage` permission; `FIELD_COLLECTION` transactions |
| `V25__susu.sql` | `susu_frequency`, `susu_plan` (one running plan per account), `susu_contribution` (only moves forward, by trigger), `susu_cycle_commission` (immutable); `susu.view`, `susu.manage` permissions |

## 2. The officer's cash

Each field officer has a balance-checked ledger account under **1130 Cash with collectors**, in their branch.

| Event | Debit | Credit |
|---|---|---|
| Field collection | 1130 ‹officer› | customer account (2110, 2130, …) |
| Remittance to a teller | 1110 ‹teller's drawer› | 1130 ‹officer› |

The account can never go below zero, so an officer can never hand over more than they collected. The **cash position** (`GET /field/officers/{id}/cash-position`) sets the ledger balance against posted collections that were not reversed, minus remittances; `reconciled` is true when they are equal. Remittances need the teller's open till, are idempotent on their request key, and the teller is never the officer (application check and database `CHECK`).

## 3. Offline collections

A phone works offline and syncs when it can (`POST /field/sync`, up to 100 collections and 100 visits per call).

- **Client reference.** Every collection carries a UUID made on the phone when the cash was taken. The server stores it with a hash of what defines the collection (device, number, customer, account, plan, amount, currency, time taken). A resend of the same collection is answered from what was stored (`DUPLICATE`, with the original outcome); a different collection under a used reference is a `CONFLICT`: nothing happens and a supervisor is alerted.
- **Device sequence.** Each registered phone numbers its collections 1, 2, 3, … (unique per device in the database). A rejected collection is still recorded, so its number is accounted for. After every sync the server works out the device's gaps; each gap raises a `SEQUENCE_GAP` alert that closes by itself when the missing collections arrive (or part of them, and the rest gets a new alert). The sync answer lists the missing numbers so the phone can resend them.
- **One transaction per item.** Every collection is processed in its own transaction under the device's row lock: a rule that refuses one collection (customer not assigned to the officer, account not the customer's, not creditable, above the product's maximum balance, dated in the future, susu plan not owed that much, officer suspended) records it as `REJECTED` with the reason, and the cash stays with the officer. A rejected posting rolls back completely.
- **Limits.** Officers have an offline cash limit and an offline time limit. The phone refuses to record past them (§6); the server raises `OFFLINE_LIMIT` when one sync brings more than the limit and `LATE_SYNC` when collections arrive later than the time limit, because the cash was really taken and must still be posted.
- **Devices.** Officers register their phone (`POST /field/devices`, idempotent); a supervisor revokes a lost one. A revoked phone cannot sync (`DEVICE_NOT_REGISTERED`).
- **Visits** sync the same way, idempotent on their client reference; a visit to a customer not assigned to the officer is rejected (not stored).
- **Who sees what.** A field officer sees only their own collections, visits and remittances, even though the default role holds `collection.view`; supervisors (`field.manage`, or `collection.view` without being an officer) see the officers of their branches.

## 4. Susu

- **Frequencies** are rows per institution (`DAILY`, `WEEKLY`, `MONTHLY` by default; more can be added, e.g. every 2 weeks).
- **Plan.** On an open susu account the customer holds: contribution amount, frequency, cycle length (contributions per cycle, e.g. 31) and commission per cycle (contributions kept by the collector, fewer than the cycle). Start date not in the past; optional end date. One running plan per account (database).
- **Schedule.** Cycle 1 is scheduled when the plan opens; end-of-day schedules each next cycle, up to the end date.
- **Paying.** A field collection naming the plan must be a whole number of contributions and pays that many of the oldest unpaid ones (a missed contribution can still be paid late); more than is scheduled is refused. The posting and the contributions are one transaction.
- **End-of-day** (`SUSU_CONTRIBUTIONS`, order 250): contributions due on or before the closed date and unpaid become `MISSED`; for each cycle whose last contribution was due by then, the commission is charged once: the plan's commission contributions, but **no more than were paid** in the cycle, **no more than the account holds**, and nothing from an account that cannot pay out (Dr the account, Cr the product's fee income). Then the next cycle is scheduled, or the plan completes at its end date.
- A supervisor (`susu.manage`) can waive an expected or missed contribution, or cancel a plan.

## 5. API (all under `/api/v1`)

| Endpoint | Permission | Notes |
|---|---|---|
| `GET·POST /field/officers`, `GET·PUT /field/officers/{id}`, `POST /field/officers/{id}/status` | `field.manage` (list also `collection.view`, `teller.operate`) | registering opens the cash account |
| `GET /field/officers/{id}/cash-position` | `field.manage`, `collection.view`, `teller.operate`, `cash.view` | reconciled against the ledger |
| `GET·POST /field/assignments`, `POST /field/assignments/{id}/end` | `field.manage` (list also `collection.view`) | the customer must be in the officer's branch |
| `GET /field/devices`, `POST /field/devices/{id}/revoke` | `field.manage` (list also `collection.view`) | |
| `GET /field/alerts`, `POST /field/alerts/{id}/resolve` | `field.manage` (list also `collection.view`) | |
| `GET /field/collections`, `GET /field/visits`, `GET /field/remittances` | `field.manage`, `collection.view`, `collection.create` | an officer sees their own |
| `GET /field/me`, `GET /field/me/customers`, `POST /field/devices`, `POST /field/sync` | `collection.create` | the field app |
| `POST /field/remittances` | `teller.operate` | `Idempotency-Key` required |
| `GET·POST /susu/frequencies` | `susu.view` / `susu.manage` | |
| `GET·POST /susu/plans`, `GET /susu/plans/{id}`, `POST /susu/plans/{id}/cancel`, `POST /susu/plans/{id}/contributions/{n}/waive` | `susu.view` / `susu.manage` | |

New error codes: `OFFICER_EXISTS`, `STAFF_NOT_ACTIVE`, `NOT_A_FIELD_OFFICER`, `OFFICER_SUSPENDED`, `DEVICE_IN_USE`, `DEVICE_NOT_REGISTERED`, `DEVICE_REVOKED`, `CUSTOMER_NOT_ASSIGNED`, `ACCOUNT_NOT_FOR_CUSTOMER`, `INVALID_COLLECTION_TIME`, `ASSIGNMENT_NOT_ACTIVE`, `ALREADY_ASSIGNED`, `CUSTOMER_OTHER_BRANCH`, `ALERT_NOT_OPEN`, `CURRENCY_MISMATCH`, `REMITTANCE_ABOVE_CASH`; susu: `FREQUENCY_EXISTS`, `FREQUENCY_NOT_AVAILABLE`, `NOT_A_SUSU_ACCOUNT`, `PLAN_ALREADY_RUNNING`, `INVALID_PLAN`, `PLAN_NOT_ACTIVE`, `PLAN_MISMATCH`, `AMOUNT_NOT_WHOLE_CONTRIBUTIONS`, `OVERPAID`, `CONTRIBUTION_NOT_UNPAID`. In sync answers, conflicts carry `COLLECTION_CONFLICT` / `VISIT_CONFLICT`.

The `field.manage`, `susu.view` and `susu.manage` permissions are in the Branch Manager template for new institutions; existing institutions grant them by editing their roles (as with every permission added after onboarding).

## 6. The field app

- **Encrypted queue.** Collections, visits, the officer's customers and limits live in a Drift database on **SQLite3MultipleCiphers**, bundled by `package:sqlite3` (prebuilt for every platform, checked against published sha256 sums; selected by the `hooks.user_defines.sqlite3.source` setting in `mobile/pubspec.yaml`). The 256-bit key is generated on first use and kept in the platform keystore. The app applies the key, then proves the encrypting build is loaded (`sqlite3mc_version()`), and refuses to open the database otherwise, so collections are never written to a plain file. This replaces the SQLCipher named in the architecture: same protection, an MIT-licensed build without OpenSSL.
- **Recording.** A collection gets the device's next number and a fresh client reference in one database transaction. Amounts are kept as typed (decimal text, never a double); times are the exact UTC strings sent to the server, so a resend is byte-for-byte the same. The phone refuses to record before its first sync (it does not know its limits yet), above the officer's offline cash limit, or while a collection older than the offline hours is waiting.
- **Sync** (`SyncEngine`): registers the phone once, sends what is waiting oldest first (`QUEUED → SENT → ACCEPTED / REJECTED / CONFLICT`), applies each answer, then refreshes the officer's limits and customers. A lost answer leaves items `SENT`; the next sync sends them again unchanged and the server answers `DUPLICATE`. A revoked phone stops sending and keeps everything.
- **Screens.** Home (what is waiting, last sync, cash carried, Sync now, customers), customer (accounts and susu plans; tap one to collect; record a visit), and the list of everything recorded with the server's answer.

## 7. CMS screens

- **Field operations** (`/field`): officers (register, limits, suspend or reinstate, cash position with reconciliation, devices and revocation), customer assignments, collections (posted or rejected with the reason), visits, alerts (resolve with a note).
- **Susu** (`/susu`): plans with paid amount, arrears and next due; open a plan; plan detail with the contribution grid, commissions, waive and cancel.
- **My till**: *Receive field cash* takes an officer's cash into the drawer (idempotency key per dialog).

## 8. Exit gate results

| Gate | Evidence |
|---|---|
| Replaying the same offline batch 3× posts once | `FieldOperationsIT.replayingTheSameOfflineBatchPostsOnce`: three collections posted, then the identical batch sent three more times: every answer `DUPLICATE` with the original transaction reference, the balance unchanged, exactly 3 `FIELD_COLLECTION` transactions and 3 collection rows. On the phone, `offline_sync_test` loses the server's answer after it posted, resends, and replays the batch three more times: the server posts each collection once. |
| Device sequence gaps raise alerts | `FieldOperationsIT.aSequenceGapRaisesAnAlertThatClosesWhenTheCollectionsArrive`: numbers 1, 2, 6 raise "Collections 3 to 5 … never arrived"; 4 and 5 arriving narrow it to 3; 3 arriving closes it. Conflicting references and numbers, late syncs and offline cash above the limit raise their own alerts. |
| The officer's cash position reconciles | `FieldOperationsIT.theOfficerHandsTheCashToATellerAndThePositionReconciles`: 150.00 collected, 100.00 remitted (a retry replays, 150.01 is refused), ledger 50.00 = collected − remitted, the teller's drawer holds 100.00. Reversals are excluded from "collected" just as the reversal takes the cash back out of the ledger. |
| End-of-day stays resumable | `EndOfDayIT`'s stop-and-resume gate now also stops in `SUSU_CONTRIBUTIONS` (9 checkpoints) and compares the susu state with an uninterrupted run. |
| Susu | `SusuIT`: whole contributions only, never more than scheduled, late payment of a missed contribution, commission per cycle on what was paid (and nothing when nothing was paid), completion at the end date, waivers and cancellation. |
| Whole build | 124 backend unit tests and 177 integration tests; CMS tests; field app tests (encrypted file unreadable without its key, numbering, limits, sync answers, lost answers, revoked phone, and the collect-and-sync flow on screen). |

## 9. Known follow-ups

- **Customer receipts.** Spec risk F13 calls for an SMS or push receipt after the server posts a collection; that needs the notification providers of Phase 6/7. Until then the officer gives the collection number shown on the phone.
- **Location.** The API stores latitude and longitude, but the app does not capture them yet (no location permission flow).
- **Device keys.** Phones are identified by their registration and the officer's session; per-device signing keys (so a collection is signed on the phone) are not implemented.
- **Re-registering a revoked phone** needs its queue emptied and numbering restarted; the app does not offer it yet.
- **Rejected collections** keep the cash with the officer; returning it to the customer is a supervisor's manual process, not a workflow yet.
- **Susu at the branch.** Contributions are paid through field collections; a teller deposit into a susu account credits the account but does not mark contributions paid.
- **Loan collections** arrive with Phase 5.
- **Device builds.** The field app is tested with `flutter test`; Android and iOS builds need the SDKs and devices this machine does not have.
