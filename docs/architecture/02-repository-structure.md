# 02 — Repository Structure

A single monorepo. Each top-level application builds independently. Items marked *(Phase n)* are created when that phase starts, so the tree doesn't fill up with empty placeholders.

```text
BANKING/                                    (digital-banking-platform)
├── README.md                               quick start, links to docs
├── docker-compose.yml                      local stack: postgres, redis, banking-core (+ cms, minio, mailpit later)
├── .env.example                            placeholders only, never real secrets
├── .gitignore  .gitattributes  .editorconfig
├── .github/
│   └── workflows/
│       ├── backend.yml                     lint → unit → integration → security → image
│       ├── web.yml                         (Phase 1C)
│       └── mobile.yml                      (Phase 1D)
│
├── backend/
│   └── banking-core/
│       ├── pom.xml  mvnw  mvnw.cmd  .mvn/
│       ├── Dockerfile
│       └── src/
│           ├── main/
│           │   ├── java/com/company/banking/
│           │   │   ├── BankingCoreApplication.java
│           │   │   ├── common/
│           │   │   │   ├── api/                ApiResponse, ApiErrorResponse, PageResponse
│           │   │   │   ├── error/              ErrorCode, BankingException family, GlobalExceptionHandler
│           │   │   │   ├── persistence/        BaseEntity, TenantScopedEntity, TenantAwareJpaTransactionManager
│           │   │   │   ├── tenant/             TenantContext
│           │   │   │   ├── security/           AuthenticatedActor, CurrentActor, Permissions (catalog constants)
│           │   │   │   ├── web/                CorrelationIdFilter, RequestMetadata
│           │   │   │   ├── id/                 UuidV7
│           │   │   │   └── config/             Clock, Jackson, OpenAPI, JPA auditing
│           │   │   ├── audit/                  controller service dto entity(none, JDBC) …
│           │   │   ├── tenant/                 controller service repository entity dto mapper exception
│           │   │   ├── branch/
│           │   │   ├── iam/                    + security/ (SecurityConfig, JWT, filters) + spi/ (StaffDirectory)
│           │   │   ├── staff/
│           │   │   ├── platform/
│           │   │   ├── customer/   kyc/                                       (Phase 1B)
│           │   │   ├── accountproduct/ account/ ledger/ transaction/ charge/ approval/   (Phase 2)
│           │   │   ├── teller/ cash/ businessdate/ eod/ reconciliation/       (Phase 3)
│           │   │   ├── susu/ collection/ fieldops/                            (Phase 4)
│           │   │   ├── loan/                                                  (Phase 5)
│           │   │   ├── channel/ beneficiary/ notification/                    (Phase 6)
│           │   │   ├── payment/ integration/                                  (Phase 7)
│           │   │   ├── fraud/ compliance/                                     (Phase 8)
│           │   │   └── reporting/                                             (Phase 9)
│           │   └── resources/
│           │       ├── application.yml  application-local.yml  application-prod.yml
│           │       └── db/migration/   V1__baseline.sql  V2__tenancy.sql  V3__branch.sql …
│           └── test/
│               └── java/com/company/banking/
│                   ├── support/        IntegrationTest base, TestDatabase (Testcontainers | embedded PG), fixtures
│                   ├── architecture/   ArchitectureTest (ArchUnit)
│                   └── <module>/       *Test (unit) and *IT (integration)
│
├── web/                                                                        (Phase 1C)
│   ├── package.json  tsconfig.base.json  vitest.shared.ts      npm workspaces
│   ├── packages/api  packages/bff  packages/ui  packages/console
│   ├── institution-cms/
│   └── super-admin/
│
├── mobile/                                                                     (Phase 1D)
│   ├── pubspec.yaml  melos.yaml
│   ├── packages/banking_core  packages/banking_ui  packages/banking_api
│   ├── customer_app/
│   └── field_officer_app/
│
├── infrastructure/
│   ├── postgres/init/01-roles.sh           creates banking_migrator / banking_app roles + database
│   ├── k8s/                                (later) manifests / Helm chart
│   └── observability/                      (later) Prometheus scrape config, Grafana dashboards
│
└── docs/
    ├── README.md                           index
    ├── architecture/
    │   ├── 00-specification-review.md
    │   ├── 01-system-architecture.md
    │   ├── 02-repository-structure.md
    │   ├── 03-domain-model.md              PostgreSQL model + ERDs
    │   ├── 04-roadmap.md
    │   └── 05-phase1-blueprint.md
    ├── security/
    │   ├── authentication.md
    │   └── roles-and-permissions.md
    ├── ledger/        ledger-design.md                      (Phase 2)
    ├── loans/         loan-calculations.md                  (Phase 5)
    ├── operations/    eod.md, reconciliation.md             (Phase 3)
    ├── integrations/  provider guides                       (Phase 7)
    └── deployment/    local-development.md
```

## Naming conventions

| Thing | Convention | Example |
|---|---|---|
| Java packages | `com.company.banking.<module>.<layer>` | `com.company.banking.branch.service` |
| Tables | singular snake_case, schema `core` | `core.branch`, `core.staff_credential` |
| Constraint names | `pk_`, `fk_`, `uq_`, `ck_`, `ix_` + table + columns | `uq_branch_tenant_code` |
| Flyway | `V<n>__<module>_<what>.sql`, never edited after merge | `V3__branch.sql` |
| REST | `/api/v1/<plural-noun>`, kebab-case | `/api/v1/account-products` |
| Permissions | `<domain>.<action>` lowercase | `loan.approve` |
| Error codes | UPPER_SNAKE | `INSUFFICIENT_FUNDS` |
| Audit actions | `<ENTITY>_<PAST_TENSE>` | `BRANCH_CREATED` |
| Tests | `*Test` = unit (surefire), `*IT` = integration (failsafe) | `BranchServiceTest`, `BranchApiIT` |
