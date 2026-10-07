# Data Protection

How the platform classifies, stores, shows and logs customer data. Implemented in `common.crypto`, `customer`, `document` and `audit`. Verified by `PiiProtectionIT`, `FieldEncryptionServiceTest` and `DatabaseSecurityIT`.

## Classification

| Class | Examples | Storage | Shown in API | Audit snapshots | Application logs |
|---|---|---|---|---|---|
| **Secret** | passwords, PINs (Phase 6), OTP/TOTP secrets, refresh tokens | hash (Argon2id / SHA-256) or AES-GCM ciphertext | never | never | never |
| **Restricted identifiers** | national ID / Ghana Card, passport, tax numbers, related-party IDs | AES-GCM ciphertext + HMAC blind index + masked copy | masked; full value only via the audited reveal endpoint (`kyc.review`) | masked only | never |
| **Identity documents** | ID images, selfies, signatures, proof of address | encrypted objects in object storage + metadata row | download only with `kyc.view`, audited | metadata (type, size, hash) only | never |
| **Personal data** | names, date of birth, phone, email, addresses | plain columns (database-level encryption at rest is a hosting requirement) | to staff in branch scope | yes: needed to investigate account-takeover patterns such as phone changes | never |
| **Operational** | ids, statuses, timestamps, branch | plain | yes | yes | ids only |

## Field encryption

- **Algorithm:** AES-256-GCM, random 96-bit IV per value, 128-bit tag. Text format `v<key version>:<base64(iv‖ciphertext‖tag)>`.
- **Associated data binds every ciphertext to its place:** `<tenant>/<table>/<row id>/<field>`. A ciphertext copied into another row, field or institution fails to decrypt, which blocks a database-level "swap" attack.
- **Keys:** `banking.crypto.data-keys.<version>` (base64, 256-bit) and `banking.crypto.active-key-version`, supplied by the secret manager. The application refuses to start without them, and deployed environments refuse the development keys committed for local use and tests (`ProductionSafetyGuard`).
- **Rotation:** add a new version, make it active, and keep old versions configured until a re-encryption job (Phase 3 operations) has rewritten older values. Ciphertexts carry their key version.

## Blind indexes

`HMAC-SHA256(blind-index key, tenant | purpose | normalised value)` supports exact-match lookups and uniqueness (`uq_customer_identification_number`) without decrypting. Including the tenant means the same Ghana Card number produces unrelated index values at two institutions, so datasets can't be correlated across tenants. Normalisation: upper-case, whitespace removed, then non-alphanumerics stripped for the index (so `GHA-123456789-0` and `gha 1234567890` collide as they should).

Rotating the blind-index key requires recomputing every index, so it is treated as a planned migration, not a routine rotation.

## Masking

All identifiers are masked except the last four letters or digits, with separators kept: `GHA-123456789-0` → `***-******789-0`. The masked form is stored, so listing customers never touches ciphertext.

## Documents

1. Size limit (10 MB), then the **type is detected from the content's leading bytes**. Only JPEG, PNG and PDF are accepted; the declared content type and file name are ignored.
2. SHA-256 recorded, then malware-scanner port. Until a scanner (e.g. ClamAV) is configured, scans are recorded as `SKIPPED` so the gap is visible in data. **A scanner is a go-live requirement.**
3. Encrypted with the data key (associated data `<tenant>/stored_document/<id>`), written under a generated key `<tenant>/<year>/<document id>`. No user input reaches the storage path.
4. On read: decrypted, hash re-verified, download recorded as `CUSTOMER_DOCUMENT_VIEWED`.

## Logging rules

- Access logs record method, path (no query string), status and duration, plus correlation, tenant and actor ids.
- Database constraint errors are logged by **constraint name only**, because database messages quote the offending values.
- Request DTOs that carry secrets or identifiers override `toString()` to mask them.
- Identity-verification providers must not log the subject they receive.

## Retention (to be set with legal review)

Customer data is kept for the regulatory retention period after the relationship ends, then crypto-shredded: encrypted fields and documents become unreadable once their data key version is destroyed. Audit and ledger data follow their own retention schedule (see [domain model §14](../architecture/03-domain-model.md)).
