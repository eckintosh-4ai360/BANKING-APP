/**
 * Permission codes of the backend catalog (core.permission). The UI only uses them to hide what the user can't do;
 * the backend enforces every one of them.
 */
export const Permission = {
  institutionView: 'institution.view',
  institutionManage: 'institution.manage',
  settingsView: 'settings.view',
  settingsManage: 'settings.manage',
  branchView: 'branch.view',
  branchManage: 'branch.manage',
  staffView: 'staff.view',
  staffCreate: 'staff.create',
  staffEdit: 'staff.edit',
  staffDisable: 'staff.disable',
  staffUnlock: 'staff.unlock',
  roleView: 'role.view',
  roleManage: 'role.manage',
  roleAssign: 'role.assign',
  permissionView: 'permission.view',
  auditView: 'audit.view',
  customerView: 'customer.view',
  customerCreate: 'customer.create',
  customerEdit: 'customer.edit',
  customerFreeze: 'customer.freeze',
  kycView: 'kyc.view',
  kycReview: 'kyc.review',
  kycApprove: 'kyc.approve',
  productView: 'product.view',
  productManage: 'product.manage',
  accountView: 'account.view',
  accountCreate: 'account.create',
  accountFreeze: 'account.freeze',
  accountClose: 'account.close',
  transactionView: 'transaction.view',
  transactionCreate: 'transaction.create',
  transactionReverse: 'transaction.reverse',
  ledgerView: 'ledger.view',
  ledgerPost: 'ledger.post',
  approvalView: 'approval.view',
  approvalAct: 'approval.act',
  tellerOperate: 'teller.operate',
  tellerSupervise: 'teller.supervise',
  cashView: 'cash.view',
  cashManage: 'cash.manage',
  operationsView: 'operations.view',
  operationsManage: 'operations.manage',
  platformTenantView: 'platform.tenant.view',
  platformTenantManage: 'platform.tenant.manage',
  platformFeatureManage: 'platform.feature.manage',
  platformAuditView: 'platform.audit.view',
} as const;

export type PermissionCode = (typeof Permission)[keyof typeof Permission];

/** True when the user holds at least one of the permissions (or none are required). */
export function hasAny(granted: readonly string[] | undefined, required: readonly string[] | undefined): boolean {
  if (!required || required.length === 0) {
    return true;
  }
  return required.some((permission) => granted?.includes(permission) ?? false);
}
