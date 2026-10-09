# Digital Banking & Microfinance Platform

A multi-tenant core banking and microfinance platform for African financial institutions: microfinance companies, savings and loans, credit unions, susu operators, rural/community banks and digital lenders.

| Component | Stack | Status |
|---|---|---|
| `backend/banking-core` | Java 21, Spring Boot 4.1 modular monolith, PostgreSQL 18, Redis, Flyway | **Phases 1A, 1B and 2 complete** |
| `web/institution-cms`, `web/super-admin` | Next.js 16, React 19, TypeScript, Tailwind 4, TanStack Query/Table, RHF + Zod | **Phase 1C complete** (Playwright smoke suite needs a running stack) |
| `mobile/customer_app`, `mobile/field_officer_app` | Flutter 3.47, Riverpod 3, GoRouter 18, Dio 5, secure storage | **Phase 1D complete** (tested with `flutter test`; device builds not yet produced) |

Start with the architecture docs in [`docs/`](docs/README.md). The [specification review](docs/architecture/00-specification-review.md) explains the key decisions, and the [roadmap](docs/architecture/04-roadmap.md) gives the build order and exit gates.

## What exists today

### Phase 1A: platform foundation

- **Multi-tenancy, three layers deep:** tenant taken only from the verified token, tenant-scoped repositories, **PostgreSQL row-level security** with a restricted runtime role, and composite tenant foreign keys.
- **Identity & access:** staff and platform logins, RS256 access tokens, single-use rotating refresh tokens with theft detection, per-request session revocation, Argon2id passwords, lockout, rate limiting, temporary passwords that must be changed.
- **Granular RBAC:** 60 permissions, 7 default role templates with segregation of duties, branch data scope, anti-escalation rules.
- **Institutions:** platform onboarding (institution, licences, roles, head office, first administrator in one transaction), suspension, feature licensing, white-label branding.
- **Branches & staff** administration with optimistic locking.
- **Immutable audit trail**, append-only at the database level, with before/after snapshots and correlation ids.
- **TOTP two-step verification** for staff and platform operators (mandatory for operators in deployed environments).

### Phase 1B: customers and KYC

- **Customer records:** individuals and businesses, addresses, identifications, next of kin, directors and beneficial owners, gap-free check-digit customer numbers.
- **Data protection:** identity and tax numbers encrypted with AES-256-GCM (versioned keys), searchable through per-tenant HMAC blind indexes, masked in every response; reveal is separately permissioned and audited.
- **Documents:** content sniffing (JPEG/PNG/PDF only), SHA-256, encryption at rest, malware-scan hook.
- **KYC workflow:** configurable tiers and identification types, requirement evaluation, electronic identity verification port, and a four-eyes rule enforced in the service *and* by a database constraint.
- **Customer search** under 300 ms at 100,000 customers, with row-level security intact.

### Phase 1C: web consoles

- **Institution CMS:** dashboard, customer search and onboarding, customer record (identity, addresses, documents with secure viewer, related parties), KYC queue and case decisions, branches, staff (one-time temporary passwords), roles and permissions, audit log, institution settings (profile, branding, features, KYC tiers, ID types), account security.
- **Platform console:** institution onboarding, licensing, suspension, platform audit. No route to tenant customer data.
- **Backend-for-frontend:** tokens never reach browser JavaScript (sealed HttpOnly SameSite=Strict cookies), CSRF header + Origin checks, single-flight token refresh, allow-listed proxy, nonce-based Content-Security-Policy.

### Phase 1D: mobile apps

- **Field officer app:** staff sign-in with two-step verification and forced change of temporary passwords, officer home, inactivity sign-out.
- **Customer app:** white-label build per institution that loads its branding first; sign-in only when the institution enabled mobile banking.
- **Shared core:** access token in memory only, refresh token in the platform keystore, single-flight token refresh, exact-decimal `Money` with no floating point (enforced by a test).

### Phase 2: banking core

- **Double-entry ledger:** chart of accounts per institution type, accounting periods, sub-ledger accounts and one posting engine. The database itself refuses unbalanced journals, changes to posted entries, postings into closed periods and overdrawn customer balances.
- **Deposit products** with versioned terms: accounts keep the terms they were opened under. Limits, minimum and maximum balances, KYC tier and charges (flat or percentage) are part of the terms.
- **Accounts:** single, joint and business ownership, holds, restrict / freeze / close, statements from the ledger as JSON, PDF or CSV.
- **Money movement:** deposits, withdrawals and transfers that are idempotent on retry, run in one database transaction with their journal, audit entry and outbox event, and never overdraw under concurrency.
- **Maker-checker:** reversals and manual journals always, withdrawals and transfers above an institution's thresholds; the checker must be a different person, and the database enforces it.

### Tests

**243 backend tests** (unit, ArchUnit, integration against real PostgreSQL), **119 web tests** (Vitest + Testing Library) and **69 mobile tests** (Flutter unit and widget tests). They cover cross-tenant isolation, RLS, token reuse, lockout, the permission matrix, PII in logs, the four-eyes rule, ledger invariants, idempotent retries, concurrent withdrawals and transfers, maker-checker, session sealing, CSRF, refresh races, proxy allow-lists and form validation.

## Quick start

Prerequisites: JDK 21+, Docker Desktop.

```bash
cp .env.example .env
docker compose up -d                     # PostgreSQL 18 + Redis
cd backend/banking-core
./mvnw spring-boot:run -Dspring-boot.run.profiles=local        # Windows: mvnw.cmd
```

- Swagger UI: <http://localhost:8080/swagger-ui.html> (groups: *institution*, *platform*)
- Health: <http://localhost:8080/actuator/health>

### Demo data (`local` profile only)

| Login | Institution code | Username | Password |
|---|---|---|---|
| Platform owner | (platform login) | `superadmin` | `Platform@Owner2026` |
| Institution admin | `demo-mfi` | `admin` | `Demo@Pass2026` |
| Branch manager / teller / loan officer / field officer / compliance / auditor | `demo-mfi` | `manager`, `teller`, `loanofficer`, `fieldofficer`, `compliance`, `auditor` | `Demo@Pass2026` |

These credentials exist only in local databases seeded by the `local` profile. Deployed environments refuse to start with local settings.

```bash
curl -s localhost:8080/api/v1/auth/staff/login -H 'Content-Type: application/json' \
  -d '{"tenantCode":"demo-mfi","username":"admin","password":"Demo@Pass2026"}'
```

### Web consoles

Prerequisite: Node.js 24 LTS (22.22+ works).

```bash
cd web
npm install
npm run dev:cms        # http://localhost:3000, institution code demo-mfi
npm run dev:admin      # http://localhost:3001, platform login
```

## Tests

```bash
cd backend/banking-core
./mvnw verify          # unit + architecture + integration (real PostgreSQL via Testcontainers or embedded)

cd web
npm run typecheck && npm test && npm run build

cd mobile
flutter analyze    # then flutter test in each package and app, see mobile/README.md
```

## Repository layout

```text
backend/banking-core/     Spring Boot modular monolith (common, audit, tenant, branch, iam, staff, platform, customer, kyc, document,
                          ledger, product, account, transaction, approval, manualjournal)
web/                      Next.js apps + shared packages (api, bff, ui, console)
mobile/                   Flutter pub workspace: customer and field officer apps + shared packages
infrastructure/           PostgreSQL init scripts; Kubernetes/observability later
docs/                     Architecture, domain model, roadmap, security, deployment
docker-compose.yml        Local stack
```

## Ground rules

Money is never `float`/`double` (Java `BigDecimal`, JSON decimal strings). Posted transactions and ledger entries are never changed or deleted. Tenant identity is never taken from client input. Secrets never enter the repository. Every financial calculation has tests. The full list is in the specification and enforced through the architecture docs.

This software does not by itself make an institution compliant with any regulation; see the [regulatory notes](docs/architecture/00-specification-review.md#8-regulatory-notes-not-legal-advice).
