# Local Development

## Prerequisites

| Tool | Version | Used for |
|---|---|---|
| JDK | 21+ (21 LTS recommended; 22 works) | backend |
| Docker Desktop | current | PostgreSQL, Redis, containerised API |
| Maven | not required | use `./mvnw` / `mvnw.cmd` |
| Node.js + npm | 24 LTS (22.22+ works) / npm 10+ | web apps (Phase 1C) |
| Flutter | stable | mobile apps (Phase 1D) |

## Run the stack

```bash
cp .env.example .env                       # local-only credentials
docker compose up -d                       # PostgreSQL 18 + Redis
cd backend/banking-core
./mvnw spring-boot:run -Dspring-boot.run.profiles=local      # Windows: mvnw.cmd ...
```

On first start, Flyway migrates the schema, and the `local` profile seeds a demo institution (see the root README for demo users). API docs: <http://localhost:8080/swagger-ui.html>. Health: <http://localhost:8080/actuator/health>.

To run the API in a container instead: `docker compose --profile app up -d --build`.

## Tests

```bash
./mvnw test       # unit tests + ArchUnit architecture rules (~30 s)
./mvnw verify     # + integration tests against real PostgreSQL 18
```

Integration tests start PostgreSQL with **Testcontainers** when Docker is running, otherwise with **embedded PostgreSQL binaries** (first run unpacks them, about 1 minute on Windows). Force a mode with `-Dbanking.test.database=docker|embedded`. Tests connect as the restricted `banking_app` role, exactly as production does, so row-level security is genuinely exercised.

> **VS Code / Eclipse users:** the Java language server also compiles into `target/classes`. If a Maven build that runs at the same time fails with missing classes or missing MapStruct beans, run `./mvnw clean verify` again once the IDE build has finished.

## Web consoles

```bash
cd web
npm install                    # one workspace: packages/* + both apps
npm run dev:cms                # Institution CMS   → http://localhost:3000
npm run dev:admin              # Platform console  → http://localhost:3001
```

Both apps talk to banking-core at `http://localhost:8080` through their own BFF. Sign in to the CMS with institution code `demo-mfi` and a demo user; sign in to the platform console with the platform owner (see the root README).

| Variable | Default (development) | Production |
|---|---|---|
| `BACKEND_URL` | `http://localhost:8080` | required, e.g. `http://banking-core:8080` (internal network) |
| `SESSION_SECRET` | a fixed development value | **required**, ≥ 32 random characters, comma-separated list to rotate (newest first). Generate with `openssl rand -base64 48`. Different per app. |
| `APP_ORIGIN` | the request's own origin | **required**, e.g. `https://cms.bank.example` (comma-separated allowed) |
| `COOKIE_SECURE` | `false` | always `true` (enforced) |
| `SESSION_MAX_AGE_SECONDS` | `43200` (12 h) | absolute browser-session cap |
| `BACKEND_TIMEOUT_MS` | `30000` | |

The BFF refuses to start in production with a missing or development secret, insecure cookies, or no origin allow-list. CSP nonces require dynamic rendering, so every page is server-rendered per request.

```bash
npm run typecheck              # strict TypeScript, all packages and apps
npm test                       # Vitest: packages + apps
npm run build                  # production builds of both apps (output: standalone)
npm run e2e --workspace institution-cms   # Playwright smoke tests; needs the backend (local profile) and the CMS running,
                                          # and once: npx playwright install chromium
```

## Database access

| Role | Purpose | Local password (`.env`) |
|---|---|---|
| `postgres` | superuser, container admin only | `POSTGRES_SUPERUSER_PASSWORD` |
| `banking_migrator` | owns schema `core`, runs Flyway | `DB_MIGRATOR_PASSWORD` |
| `banking_app` | runtime: DML only, subject to RLS | `DB_APP_PASSWORD` |

To inspect tenant data in `psql` as `banking_app`, set the tenant first:

```sql
BEGIN;
SELECT set_config('app.tenant_id', '<tenant uuid>', true);
SELECT code, name FROM core.branch;
COMMIT;
```

Without the setting you see no tenant rows. That is RLS doing its job.

## Configuration reference

All settings live under `banking.*` in `application.yml` (documented in [05-phase1-blueprint.md §5](../architecture/05-phase1-blueprint.md)). Secrets come only from environment variables: `DB_APP_PASSWORD`, `DB_MIGRATOR_PASSWORD`, `REDIS_PASSWORD`, `JWT_PRIVATE_KEY_LOCATION`, `JWT_PUBLIC_KEY_LOCATION`, `DATA_KEY_V1` (and further versions), `BLIND_INDEX_KEY`, `PLATFORM_BOOTSTRAP_*`. Other deployment settings: `DOCUMENT_STORAGE_PATH`, `IDENTITY_VERIFICATION_PROVIDER` (`none` until an approved provider adapter exists), `MFA_ISSUER`.

The `local` profile ships fixed development encryption keys and the stub identity-verification provider (ID numbers ending in 9 "don't match"). Both are refused in deployed environments.

Generate encryption keys:

```bash
openssl rand -base64 32   # DATA_KEY_V1
openssl rand -base64 32   # BLIND_INDEX_KEY (rotating it means recomputing all blind indexes)
```

Generate a JWT key pair for deployed environments:

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out jwt-private.pem
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
# then JWT_PRIVATE_KEY_LOCATION=file:/run/secrets/jwt-private.pem (never commit the files)
```
