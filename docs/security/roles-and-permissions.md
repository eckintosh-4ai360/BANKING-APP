# Roles & Permissions

Roles are tenant-owned collections of permissions. Permissions are a fixed, global catalog (`core.permission`, mirrored by `Permissions.java`; `PermissionCatalogIT` keeps them identical). Endpoints declare exactly one permission with `@PreAuthorize("hasAuthority('<code>')")`. A test fails the build if any endpoint lacks one, unless it is deliberately public or available to every signed-in user.

## Enforcement layers

1. **URL rules**: audience separation (`staff` vs `platform`).
2. **Method security**: one permission per endpoint.
3. **Data scope**: branch scope from the token. Resources outside it are reported as `404`.
4. **Business rules**: anti-escalation (below).
5. **Database**: RLS tenant isolation; a trigger blocks platform permissions on tenant roles.

## Anti-escalation rules

- Nobody can change their own roles, status, credentials or profile through admin endpoints (`SELF_MODIFICATION_NOT_ALLOWED`).
- Nobody can edit a role they hold.
- Assigning roles while creating staff additionally requires `role.assign`.
- Only staff with all-branch access can grant all-branch access or create branches. Branch-scoped administrators only see and manage their own branches.
- Platform-scope permissions can never be granted to institution roles (service check plus database trigger).
- Maker-checker for role assignment, staff creation and role edits arrives with the approval engine (Phase 2).

## Catalog

| Area | Permissions |
|---|---|
| Institution | `institution.view`, `institution.manage`*, `settings.view`, `settings.manage`* |
| Branches | `branch.view`, `branch.manage`* |
| Staff | `staff.view`, `staff.create`*, `staff.edit`, `staff.disable`*, `staff.unlock`* |
| Access | `role.view`, `role.manage`*, `role.assign`*, `permission.view` |
| Audit | `audit.view` |
| Customers & KYC | `customer.view`, `customer.create`, `customer.edit`, `customer.freeze`*, `kyc.view`, `kyc.review`, `kyc.approve`* |
| Products & accounts | `product.view`, `product.manage`*, `account.view`, `account.create`, `account.freeze`*, `account.close`* |
| Transactions & ledger | `transaction.view`, `transaction.create`, `transaction.reverse`*, `ledger.view`, `ledger.post`* |
| Approvals | `approval.view`, `approval.act`* |
| Branch operations | `teller.operate`, `teller.supervise`*, `cash.view`, `cash.manage`*, `collection.view`, `collection.create` |
| Loans | `loan.view`, `loan.create`, `loan.assess`, `loan.recommend`, `loan.approve`*, `loan.disburse`*, `loan.writeoff`* |
| Reporting & risk | `report.view`, `report.export`, `compliance.view`, `compliance.manage`*, `fraud.view`, `fraud.manage`* |
| Platform (platform users only) | `platform.tenant.view`, `platform.tenant.manage`*, `platform.feature.manage`*, `platform.audit.view`, `platform.user.manage`* |

\* `sensitive = true`: candidates for maker-checker policies.

## Default role templates (created for every institution)

| Role | Scope of duties | Notable exclusions |
|---|---|---|
| `INSTITUTION_ADMIN` | Institution settings, branches, staff, roles, products, audit, reports | **No** money movement, loan decisions, ledger posting or approvals (segregation of duties) |
| `BRANCH_MANAGER` | Customers, KYC approval, accounts, reversals, loan approval, approvals, teller supervision, cash | No staff or role administration |
| `TELLER` | Customer/account lookup, transactions, teller drawer | No reversals |
| `LOAN_OFFICER` | Customers, loan origination, assessment, recommendation | No approval or disbursement |
| `FIELD_OFFICER` | Customer registration, collections | No transactions outside collections |
| `COMPLIANCE_OFFICER` | KYC review/approval, freezes, compliance and fraud cases, audit | No money movement |
| `AUDITOR` | Read-only across all areas, report export | Nothing that changes data |

Platform roles are fixed in code (`PlatformRole`): `PLATFORM_OWNER`, `PLATFORM_ADMIN`, `PLATFORM_SUPPORT`.
