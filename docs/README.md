# Documentation

| Document | Contents |
|---|---|
| [architecture/00-specification-review.md](architecture/00-specification-review.md) | Risks, gaps and adjustments to the master specification; decisions log |
| [architecture/01-system-architecture.md](architecture/01-system-architecture.md) | System, backend, Next.js, Flutter and infrastructure architecture |
| [architecture/02-repository-structure.md](architecture/02-repository-structure.md) | Monorepo layout and naming conventions |
| [architecture/03-domain-model.md](architecture/03-domain-model.md) | PostgreSQL domain model and ERDs for all phases; tenant isolation, ledger invariants |
| [architecture/04-roadmap.md](architecture/04-roadmap.md) | Phases, build order and exit gates |
| [architecture/05-phase1-blueprint.md](architecture/05-phase1-blueprint.md) | Phase 1 technical blueprint: migrations, API, error codes, config, tests |
| [architecture/06-phase2-blueprint.md](architecture/06-phase2-blueprint.md) | Phase 2 banking core: ledger invariants, products, accounts, money movement, maker-checker, statements |
| [security/authentication.md](security/authentication.md) | Identities, tokens, sessions, lockout, password policy |
| [security/roles-and-permissions.md](security/roles-and-permissions.md) | Permission catalog, default roles, anti-escalation rules |
| [security/data-protection.md](security/data-protection.md) | Data classification, field encryption, blind indexes, masking, documents, logging rules |
| [deployment/local-development.md](deployment/local-development.md) | Running and testing locally |

Documents for loan calculations, EOD and integrations are added in the phase that builds them.
