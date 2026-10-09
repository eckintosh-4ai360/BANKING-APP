/**
 * Contracts of the banking-core API (see the backend OpenAPI document at /v3/api-docs/institution and /platform).
 * Dates are ISO-8601 strings; money amounts (from Phase 2) are decimal strings and must never be parsed into
 * JavaScript numbers.
 */

export type Uuid = string;
export type IsoDateTime = string;
export type IsoDate = string;

export interface ApiEnvelope<T> {
  success: true;
  message: string;
  data: T;
  timestamp: IsoDateTime;
}

export interface FieldViolation {
  field: string;
  message: string;
}

export interface ApiErrorBody {
  success: false;
  code: string;
  message: string;
  timestamp: IsoDateTime;
  traceId?: string;
  errors?: FieldViolation[];
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

// ------------------------------------------------------------------------------------------------- identity

export interface RoleSummary {
  id: Uuid;
  code: string;
  name: string;
}

export interface Me {
  id: Uuid;
  tenantId: Uuid;
  tenantCode: string;
  institutionName: string;
  username: string;
  firstName: string;
  lastName: string;
  email: string;
  homeBranchId: Uuid;
  allBranchesAccess: boolean;
  roles: RoleSummary[];
  permissions: string[];
  passwordChangeRequired: boolean;
}

export interface PlatformMe {
  id: Uuid;
  username: string;
  fullName: string;
  email: string;
  role: string;
  permissions: string[];
  passwordChangeRequired: boolean;
}

/** What the BFF tells the browser after a credential exchange. Tokens never leave the server. */
export interface SignInResult {
  mfaRequired: boolean;
  passwordChangeRequired: boolean;
  mfaEnrollmentRequired: boolean;
}

export interface MfaSetup {
  secret: string;
  otpauthUri: string;
}

export interface PermissionInfo {
  code: string;
  module: string;
  description: string;
  sensitive: boolean;
}

export interface Role {
  id: Uuid;
  code: string;
  name: string;
  description: string | null;
  systemRole: boolean;
  status: 'ACTIVE' | 'INACTIVE';
  permissions: string[];
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
  version: number;
}

// ---------------------------------------------------------------------------------------------- institution

export interface TenantSummary {
  id: Uuid;
  code: string;
  legalName: string;
  displayName: string;
  institutionType: string;
  status: 'ONBOARDING' | 'ACTIVE' | 'SUSPENDED' | 'TERMINATED';
  countryCode: string;
  baseCurrency: string;
  timezone: string;
  locale: string;
  licenceNumber: string | null;
}

export interface InstitutionProfile {
  contactEmail: string | null;
  contactPhone: string | null;
  supportEmail: string | null;
  supportPhone: string | null;
  websiteUrl: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  region: string | null;
  digitalAddress: string | null;
  logoUrl: string | null;
  primaryColor: string;
  secondaryColor: string;
  smsSenderId: string | null;
  emailSenderName: string | null;
  emailSenderAddress: string | null;
  version: number;
}

export interface FeatureState {
  code: string;
  name: string;
  description: string;
  licensed: boolean;
  enabled: boolean;
}

export interface Institution {
  institution: TenantSummary;
  profile: InstitutionProfile;
  features: FeatureState[];
}

export interface TenantDetails {
  tenant: TenantSummary;
  features: FeatureState[];
}

export interface IssuedCredential {
  username: string;
  temporaryPassword: string;
}

export interface OnboardingResult {
  tenant: TenantSummary;
  headOffice: Branch;
  administratorId: Uuid;
  administratorCredential: IssuedCredential;
}

// --------------------------------------------------------------------------------------------- branch & staff

export interface Branch {
  id: Uuid;
  code: string;
  name: string;
  branchType: 'HEAD_OFFICE' | 'BRANCH' | 'AGENCY';
  status: 'ACTIVE' | 'INACTIVE' | 'CLOSED';
  phone: string | null;
  email: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  region: string | null;
  digitalAddress: string | null;
  openedOn: IsoDate | null;
  closedOn: IsoDate | null;
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
  version: number;
}

export interface StaffSummary {
  id: Uuid;
  employeeNumber: string;
  firstName: string;
  lastName: string;
  email: string;
  jobTitle: string | null;
  homeBranchId: Uuid;
  allBranchesAccess: boolean;
  status: 'ACTIVE' | 'SUSPENDED' | 'TERMINATED';
}

export interface CredentialInfo {
  username: string;
  loginEnabled: boolean;
  mustChangePassword: boolean;
  locked: boolean;
  lockedUntil: IsoDateTime | null;
  lastLoginAt: IsoDateTime | null;
  passwordChangedAt: IsoDateTime | null;
}

export interface StaffDetail extends StaffSummary {
  phone: string | null;
  statusReason: string | null;
  login: CredentialInfo | null;
  roles: RoleSummary[];
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
  version: number;
}

export interface StaffCreated {
  staff: StaffDetail;
  credential: IssuedCredential;
}

// --------------------------------------------------------------------------------------------------- audit

export interface AuditLogEntry {
  id: Uuid;
  occurredAt: IsoDateTime;
  actorType: string;
  actorId: Uuid | null;
  actorName: string | null;
  action: string;
  outcome: 'SUCCESS' | 'FAILURE' | 'DENIED';
  resourceType: string;
  resourceId: string | null;
  resourceReference: string | null;
  branchId: Uuid | null;
  before: unknown;
  after: unknown;
  metadata: Record<string, unknown> | null;
  ipAddress: string | null;
  userAgent: string | null;
  deviceId: string | null;
  correlationId: string | null;
}

// ------------------------------------------------------------------------------------------------ customers

export type CustomerStatus = 'PENDING' | 'ACTIVE' | 'DORMANT' | 'RESTRICTED' | 'FROZEN' | 'CLOSED';
export type KycStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'PENDING_REVIEW' | 'VERIFIED' | 'REJECTED' | 'EXPIRED';

export interface CustomerSummary {
  id: Uuid;
  customerNumber: string;
  customerType: 'INDIVIDUAL' | 'BUSINESS' | 'GROUP';
  displayName: string;
  primaryPhone: string | null;
  status: CustomerStatus;
  kycStatus: KycStatus;
  kycTierCode: string | null;
  riskLevel: 'UNASSESSED' | 'LOW' | 'MEDIUM' | 'HIGH';
  homeBranchId: Uuid;
  createdAt: IsoDateTime;
}

export interface IndividualProfile {
  title: string | null;
  firstName: string;
  middleName: string | null;
  lastName: string;
  dateOfBirth: IsoDate;
  gender: string | null;
  nationality: string | null;
  maritalStatus: string | null;
  occupation: string | null;
  employerName: string | null;
  employmentStatus: string | null;
  monthlyIncomeBand: string | null;
  taxIdMasked: string | null;
}

export interface BusinessProfile {
  registeredName: string;
  tradingName: string | null;
  registrationNumber: string;
  registrationDate: IsoDate | null;
  businessType: string;
  industrySector: string | null;
  annualTurnoverBand: string | null;
  numberOfEmployees: number | null;
  taxIdMasked: string | null;
}

export interface Address {
  id: Uuid;
  addressType: string;
  line1: string;
  line2: string | null;
  city: string | null;
  district: string | null;
  region: string | null;
  countryCode: string;
  digitalAddress: string | null;
  landmark: string | null;
  primary: boolean;
  active: boolean;
  verifiedAt: IsoDateTime | null;
  version: number;
}

export interface Identification {
  id: Uuid;
  idTypeCode: string;
  idNumberMasked: string;
  issuingCountry: string | null;
  issueDate: IsoDate | null;
  expiryDate: IsoDate | null;
  primary: boolean;
  active: boolean;
  verificationStatus: 'UNVERIFIED' | 'VERIFIED' | 'FAILED';
  verifiedAt: IsoDateTime | null;
  verificationReference: string | null;
}

export interface NextOfKin {
  id: Uuid;
  fullName: string;
  relationship: string;
  phone: string | null;
  email: string | null;
  address: string | null;
  primary: boolean;
  active: boolean;
  version: number;
}

export interface RelatedParty {
  id: Uuid;
  relatedCustomerId: Uuid | null;
  fullName: string;
  partyRole: string;
  ownershipPercent: string | null;
  nationality: string | null;
  dateOfBirth: IsoDate | null;
  phone: string | null;
  email: string | null;
  idTypeCode: string | null;
  idNumberMasked: string | null;
  politicallyExposed: boolean;
  active: boolean;
  version: number;
}

export interface CustomerDocument {
  id: Uuid;
  documentType: string;
  reviewStatus: 'PENDING_REVIEW' | 'ACCEPTED' | 'REJECTED';
  reviewNote: string | null;
  reviewedBy: Uuid | null;
  reviewedAt: IsoDateTime | null;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  scanStatus: string;
  uploadedBy: Uuid | null;
  uploadedAt: IsoDateTime;
}

export interface CustomerDetail extends Omit<CustomerSummary, 'createdAt'> {
  statusReason: string | null;
  kycVerifiedAt: IsoDateTime | null;
  email: string | null;
  preferredLanguage: string | null;
  relationshipOfficerId: Uuid | null;
  onboardingChannel: string;
  individual: IndividualProfile | null;
  business: BusinessProfile | null;
  addresses: Address[];
  identifications: Identification[];
  nextOfKin: NextOfKin[];
  relatedParties: RelatedParty[];
  documents: CustomerDocument[];
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
  version: number;
}

// ------------------------------------------------------------------------------------------------------ KYC

export type KycCaseStatus = 'OPEN' | 'PENDING_REVIEW' | 'RETURNED' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export interface KycCaseSummary {
  id: Uuid;
  customerId: Uuid;
  customerNumber: string | null;
  customerName: string | null;
  branchId: Uuid;
  caseType: string;
  targetTierCode: string;
  status: KycCaseStatus;
  submittedBy: Uuid | null;
  submittedAt: IsoDateTime | null;
  createdAt: IsoDateTime;
}

export interface RequirementStatus {
  code: string;
  description: string;
  metForSubmission: boolean;
  metForApproval: boolean;
}

export interface KycCheck {
  id: Uuid;
  checkType: string;
  method: 'ELECTRONIC' | 'MANUAL';
  provider: string | null;
  result: 'PASS' | 'FAIL' | 'INCONCLUSIVE' | 'ERROR';
  score: string | number | null;
  providerReference: string | null;
  note: string | null;
  performedBy: Uuid | null;
  performedAt: IsoDateTime;
}

export interface KycCase {
  id: Uuid;
  customerId: Uuid;
  customerNumber: string;
  customerName: string;
  customerKycStatus: KycStatus;
  branchId: Uuid;
  caseType: string;
  targetTierCode: string;
  status: KycCaseStatus;
  openedBy: Uuid | null;
  submittedBy: Uuid | null;
  submittedAt: IsoDateTime | null;
  decidedBy: Uuid | null;
  decidedAt: IsoDateTime | null;
  decisionNote: string | null;
  assignedRiskLevel: string | null;
  requirements: RequirementStatus[];
  checks: KycCheck[];
  createdAt: IsoDateTime;
  version: number;
}

export interface KycTier {
  code: string;
  name: string;
  description: string | null;
  tierRank: number;
  requiresIdentification: boolean;
  requiresIdDocument: boolean;
  requiresSelfie: boolean;
  requiresAddress: boolean;
  requiresProofOfAddress: boolean;
  requiresIdentityVerification: boolean;
  requiresNextOfKin: boolean;
  requiresSignature: boolean;
  requiresEmploymentInfo: boolean;
  active: boolean;
  version: number;
}

// ---------------------------------------------------------------------------------------- banking core (Phase 2)

/** Exact decimal string from the backend, e.g. "1250.00". Never parse it into a JavaScript number. */
export type Amount = string;

export interface Currency {
  code: string;
  name: string;
  minorUnits: number;
}

export type ProductType = 'SAVINGS' | 'CURRENT' | 'SUSU' | 'FIXED_DEPOSIT' | 'TARGET_SAVINGS';
export type ChargeEvent = 'CASH_DEPOSIT' | 'CASH_WITHDRAWAL' | 'TRANSFER_OUT';

export interface Charge {
  event: ChargeEvent;
  name: string;
  calculation: 'FLAT' | 'PERCENT';
  flatAmount: Amount | null;
  rate: string | null;
  minAmount: Amount | null;
  maxAmount: Amount | null;
}

export interface ProductVersion {
  id: Uuid;
  versionNo: number;
  status: 'DRAFT' | 'PUBLISHED' | 'RETIRED';
  currency: string;
  depositGlId: Uuid;
  feeIncomeGlId: Uuid | null;
  interestExpenseGlId: Uuid | null;
  minOpeningBalance: Amount;
  minOperatingBalance: Amount;
  maxBalance: Amount | null;
  interestRate: string;
  interestCalcMethod: string;
  interestPostingFrequency: string;
  dayCount: string;
  dormancyDays: number;
  requiredKycTier: string | null;
  allowOverdraft: boolean;
  maxOverdraftLimit: Amount;
  maxWithdrawalAmount: Amount | null;
  dailyWithdrawalLimit: Amount | null;
  createdAt: IsoDateTime;
  publishedAt: IsoDateTime | null;
  charges: Charge[];
}

export interface Product {
  id: Uuid;
  code: string;
  name: string;
  productType: ProductType;
  description: string | null;
  status: 'ACTIVE' | 'INACTIVE';
  currentVersion: ProductVersion | null;
  versions: ProductVersion[];
  version: number;
}

/** Terms as sent to the backend; amounts stay strings. */
export interface ProductTermsInput {
  currency: string;
  minOpeningBalance?: Amount;
  minOperatingBalance?: Amount;
  maxBalance?: Amount;
  interestRate?: string;
  dormancyDays?: number;
  requiredKycTier?: string;
  allowOverdraft: boolean;
  maxWithdrawalAmount?: Amount;
  dailyWithdrawalLimit?: Amount;
  charges?: Charge[];
}

export type AccountStatus = 'PENDING' | 'ACTIVE' | 'RESTRICTED' | 'FROZEN' | 'DORMANT' | 'CLOSED';
export type OwnershipType = 'SINGLE' | 'JOINT_ANY' | 'JOINT_ALL' | 'BUSINESS';

export interface AccountSummary {
  id: Uuid;
  accountNumber: string;
  title: string;
  customerId: Uuid;
  productCode: string;
  productType: ProductType;
  branchId: Uuid;
  currency: string;
  status: AccountStatus;
  ledgerBalance: Amount;
  availableBalance: Amount;
  openedOn: IsoDate;
}

export interface AccountHolder {
  customerId: Uuid;
  customerNumber: string | null;
  displayName: string | null;
  role: 'PRIMARY' | 'JOINT' | 'SIGNATORY';
}

export interface Account {
  id: Uuid;
  accountNumber: string;
  title: string;
  customerId: Uuid;
  productId: Uuid;
  productCode: string;
  productName: string;
  productType: ProductType;
  productVersionId: Uuid;
  branchId: Uuid;
  currency: string;
  status: AccountStatus;
  statusReason: string | null;
  ownershipType: OwnershipType;
  holders: AccountHolder[];
  ledgerBalance: Amount;
  holdAmount: Amount;
  availableBalance: Amount;
  overdraftLimit: Amount;
  openedOn: IsoDate;
  activatedAt: IsoDateTime | null;
  closedOn: IsoDate | null;
  lastActivityAt: IsoDateTime | null;
  version: number;
}

export interface Hold {
  id: Uuid;
  accountId: Uuid;
  amount: Amount;
  currency: string;
  holdType: 'LIEN' | 'PENDING_PAYMENT' | 'LEGAL' | 'FRAUD_REVIEW' | 'LOAN_COLLATERAL';
  status: 'ACTIVE' | 'RELEASED' | 'CONSUMED' | 'EXPIRED';
  reason: string;
  reference: string | null;
  expiresAt: IsoDateTime | null;
  placedAt: IsoDateTime;
  placedBy: Uuid | null;
  releasedAt: IsoDateTime | null;
  releasedBy: Uuid | null;
  releaseReason: string | null;
  version: number;
}

export type TransactionType = 'CASH_DEPOSIT' | 'CASH_WITHDRAWAL' | 'TRANSFER';

export interface Transaction {
  id: Uuid;
  reference: string;
  transactionType: TransactionType;
  status: 'POSTED' | 'REVERSED';
  channel: string;
  currency: string;
  amount: Amount;
  feeAmount: Amount;
  debitAccountId: Uuid | null;
  debitAccountNumber: string | null;
  creditAccountId: Uuid | null;
  creditAccountNumber: string | null;
  branchId: Uuid;
  journalEntryId: Uuid;
  businessDate: IsoDate;
  valueDate: IsoDate;
  narration: string | null;
  externalReference: string | null;
  initiatedBy: Uuid | null;
  approvedBy: Uuid | null;
  approvalRequestId: Uuid | null;
  createdAt: IsoDateTime;
  reversedAt: IsoDateTime | null;
  reversedBy: Uuid | null;
  reversalJournalEntryId: Uuid | null;
  reversalReason: string | null;
}

export type ApprovalType =
  | 'TRANSACTION_REVERSAL'
  | 'MANUAL_JOURNAL'
  | 'CASH_WITHDRAWAL'
  | 'TRANSFER'
  | 'LOAN_RESTRUCTURE'
  | 'LOAN_WRITE_OFF';
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CANCELLED';

export interface Approval {
  id: Uuid;
  requestType: ApprovalType;
  status: ApprovalStatus;
  branchId: Uuid;
  amount: Amount | null;
  currency: string | null;
  resourceType: string | null;
  resourceId: Uuid | null;
  summary: string;
  payload: unknown;
  requestedBy: Uuid;
  requestedAt: IsoDateTime;
  decidedBy: Uuid | null;
  decidedAt: IsoDateTime | null;
  decisionNote: string | null;
  resultResourceId: Uuid | null;
  version: number;
}

export interface ApprovalPolicy {
  requestType: 'CASH_WITHDRAWAL' | 'TRANSFER';
  currency: string;
  thresholdAmount: Amount;
  active: boolean;
  updatedAt: IsoDateTime;
  updatedBy: Uuid | null;
}

export interface BalanceAfter {
  accountId: Uuid;
  accountNumber: string;
  ledgerBalance: Amount;
  availableBalance: Amount;
}

/** Result of a deposit, withdrawal or transfer: posted, or waiting for a checker. */
export interface MovementResult {
  outcome: 'POSTED' | 'PENDING_APPROVAL';
  transaction: Transaction | null;
  balances: BalanceAfter[];
  approval: Approval | null;
}

export interface StatementLine {
  date: IsoDate;
  valueDate: IsoDate;
  reference: string | null;
  description: string | null;
  debit: Amount | null;
  credit: Amount | null;
  balance: Amount;
}

export interface AccountStatement {
  institutionName: string;
  accountId: Uuid;
  accountNumber: string;
  accountTitle: string;
  holders: string[];
  productName: string;
  branchName: string;
  currency: string;
  from: IsoDate;
  to: IsoDate;
  openingBalance: Amount;
  totalDebits: Amount;
  totalCredits: Amount;
  closingBalance: Amount;
  generatedAt: IsoDateTime;
  lines: StatementLine[];
}

// ---------------------------------------------------------------------------------------------- branch operations

export interface BusinessDate {
  businessDate: IsoDate;
  previousBusinessDate: IsoDate | null;
  /** Where end-of-day will move the business date. */
  nextBusinessDate: IsoDate;
  workingDays: string[];
  calendarVersion: number | null;
}

export interface Holiday {
  date: IsoDate;
  name: string;
  createdAt: IsoDateTime;
  createdBy: Uuid | null;
}

export type EodRunStatus = 'RUNNING' | 'FAILED' | 'COMPLETED';
export type EodStepStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED';

export interface EodStep {
  code: string;
  order: number;
  status: EodStepStatus;
  result: Record<string, unknown> | null;
  attempts: number;
  startedAt: IsoDateTime | null;
  finishedAt: IsoDateTime | null;
  error: string | null;
}

export interface EodRun {
  id: Uuid;
  /** The business date the run closes. */
  businessDate: IsoDate;
  nextBusinessDate: IsoDate;
  status: EodRunStatus;
  startedAt: IsoDateTime;
  startedBy: Uuid | null;
  finishedAt: IsoDateTime | null;
  failedStep: string | null;
  failureMessage: string | null;
  attempts: number;
  steps: EodStep[];
}

export interface Vault {
  id: Uuid;
  branchId: Uuid;
  currency: string;
  name: string;
  status: 'ACTIVE' | 'CLOSED';
  /** Cash held, from the ledger. */
  balance: Amount;
  version: number;
}

export interface Drawer {
  id: Uuid;
  branchId: Uuid;
  currency: string;
  code: string;
  name: string;
  status: 'ACTIVE' | 'INACTIVE' | 'CLOSED';
  /** Cash the drawer should hold, from the ledger. */
  balance: Amount;
  /** The open or balancing session on the drawer, if any. */
  sessionId: Uuid | null;
  tellerId: Uuid | null;
  version: number;
}

export type TellerSessionStatus = 'OPEN' | 'BALANCING' | 'CLOSED' | 'CLOSED_WITH_DIFFERENCE';

export interface TellerSession {
  id: Uuid;
  drawerId: Uuid;
  drawerCode: string;
  branchId: Uuid;
  tellerId: Uuid;
  businessDate: IsoDate;
  status: TellerSessionStatus;
  currency: string;
  openingBalance: Amount;
  /** The drawer's ledger balance now: the cash the teller should hold. */
  currentBalance: Amount;
  expectedClosingBalance: Amount | null;
  countedBalance: Amount | null;
  /** Counted minus expected; negative is a shortage. */
  difference: Amount | null;
  openedAt: IsoDateTime;
  closedAt: IsoDateTime | null;
  supervisorId: Uuid | null;
  closeNote: string | null;
  version: number;
}

/** Cash counted note by note: denomination (decimal string, e.g. "200" or "0.50") to number of pieces. */
export interface CashCount {
  denominations: Record<string, number>;
}

export type CashMovementType = 'VAULT_TO_DRAWER' | 'DRAWER_TO_VAULT' | 'VAULT_TO_VAULT' | 'BANK_TO_VAULT' | 'VAULT_TO_BANK';
export type CashMovementStatus = 'REQUESTED' | 'IN_TRANSIT' | 'COMPLETED' | 'REJECTED' | 'CANCELLED';

export interface CashMovement {
  id: Uuid;
  reference: string;
  movementType: CashMovementType;
  status: CashMovementStatus;
  currency: string;
  amount: Amount;
  fromBranchId: Uuid | null;
  toBranchId: Uuid | null;
  fromVaultId: Uuid | null;
  fromDrawerId: Uuid | null;
  toVaultId: Uuid | null;
  toDrawerId: Uuid | null;
  note: string | null;
  requestedBy: Uuid;
  requestedAt: IsoDateTime;
  approvedBy: Uuid | null;
  approvedAt: IsoDateTime | null;
  receivedBy: Uuid | null;
  receivedAt: IsoDateTime | null;
  rejectionReason: string | null;
  version: number;
}

export type CashPositionStatus = 'MATCHED' | 'BREAK' | 'NOT_COUNTED';

/** A vault's or drawer's cash at the end of a business date (end-of-day cash reconciliation). */
export interface CashPosition {
  businessDate: IsoDate;
  cashPointId: Uuid;
  cashPointType: 'VAULT' | 'DRAWER';
  branchId: Uuid;
  currency: string;
  ledgerBalance: Amount;
  /** Cash last counted in it (closing count of the drawer's last session); null for vaults. */
  countedBalance: Amount | null;
  tellerSessionId: Uuid | null;
  countedAt: IsoDateTime | null;
  /** Counted minus ledger; anything but zero is a break. */
  difference: Amount | null;
  status: CashPositionStatus;
}

export interface AuditSeal {
  id: Uuid;
  sequenceNo: number;
  rangeStart: IsoDateTime;
  rangeEnd: IsoDateTime;
  rowCount: number;
  merkleRoot: string;
  previousHash: string;
  sealHash: string;
  keyVersion: number;
  signature: string;
  createdAt: IsoDateTime;
}

export type AuditSealProblemCode = 'ROWS_CHANGED' | 'SEAL_ALTERED' | 'CHAIN_BROKEN' | 'SIGNATURE_INVALID' | 'KEY_UNAVAILABLE';

export interface AuditVerificationReport {
  from: IsoDateTime;
  to: IsoDateTime;
  sealsChecked: number;
  rowsChecked: number;
  /** End of the trail's last seal; rows after it are not sealed yet. */
  sealedThrough: IsoDateTime | null;
  intact: boolean;
  problems: { sequenceNo: number; rangeStart: IsoDateTime; rangeEnd: IsoDateTime; code: AuditSealProblemCode; detail: string }[];
}

// ------------------------------------------------------------------------------------------- field operations

export interface FieldOfficer {
  staffId: Uuid;
  firstName: string;
  lastName: string;
  branchId: Uuid;
  currency: string;
  status: 'ACTIVE' | 'SUSPENDED';
  dailyTarget: Amount | null;
  maxOfflineAmount: Amount;
  maxOfflineHours: number;
  /** Cash the officer carries now, from the ledger. */
  cashBalance: Amount;
  assignedCustomers: number;
  openAlerts: number;
  createdAt: IsoDateTime;
  version: number;
}

/** The officer's cash: the ledger against collections minus remittances. */
export interface OfficerCashPosition {
  officerId: Uuid;
  currency: string;
  businessDate: IsoDate;
  ledgerBalance: Amount;
  collected: Amount;
  remitted: Amount;
  expected: Amount;
  difference: Amount;
  reconciled: boolean;
  collectedToday: Amount;
  remittedToday: Amount;
}

export interface CustomerAssignment {
  id: Uuid;
  customerId: Uuid;
  customerNumber: string | null;
  customerName: string | null;
  officerId: Uuid;
  assignedAt: IsoDateTime;
  assignedBy: Uuid | null;
  endedAt: IsoDateTime | null;
  endReason: string | null;
}

export interface FieldDevice {
  id: Uuid;
  officerId: Uuid;
  name: string;
  lastSequenceNo: number;
  registeredAt: IsoDateTime;
  lastSyncedAt: IsoDateTime | null;
  live: boolean;
  revokedAt: IsoDateTime | null;
  revokeReason: string | null;
  version: number;
}

export interface FieldCollection {
  id: Uuid;
  clientReference: Uuid;
  deviceId: Uuid;
  sequenceNo: number;
  officerId: Uuid;
  customerId: Uuid;
  targetType: 'SAVINGS_ACCOUNT' | 'SUSU_PLAN';
  accountId: Uuid;
  susuPlanId: Uuid | null;
  amount: Amount;
  currency: string;
  collectedAt: IsoDateTime;
  receivedAt: IsoDateTime;
  status: 'POSTED' | 'REJECTED';
  rejectionCode: string | null;
  rejectionReason: string | null;
  transactionId: Uuid | null;
  businessDate: IsoDate | null;
  latitude: string | null;
  longitude: string | null;
  note: string | null;
}

export interface CustomerVisit {
  id: Uuid;
  clientReference: Uuid;
  officerId: Uuid;
  customerId: Uuid;
  purpose: string;
  outcome: string;
  notes: string | null;
  visitedAt: IsoDateTime;
  receivedAt: IsoDateTime;
  latitude: string | null;
  longitude: string | null;
}

export type FieldAlertType = 'SEQUENCE_GAP' | 'CONFLICT' | 'LATE_SYNC' | 'OFFLINE_LIMIT';

export interface FieldAlert {
  id: Uuid;
  officerId: Uuid;
  deviceId: Uuid | null;
  alertType: FieldAlertType;
  detail: string;
  missingFrom: number | null;
  missingTo: number | null;
  clientReference: Uuid | null;
  status: 'OPEN' | 'RESOLVED';
  raisedAt: IsoDateTime;
  resolvedAt: IsoDateTime | null;
  resolvedBy: Uuid | null;
  resolution: string | null;
  version: number;
}

export interface CollectorRemittance {
  id: Uuid;
  reference: string;
  officerId: Uuid;
  tellerId: Uuid;
  drawerId: Uuid;
  drawerCode: string | null;
  branchId: Uuid;
  amount: Amount;
  currency: string;
  businessDate: IsoDate;
  note: string | null;
  createdAt: IsoDateTime;
  /** What the officer still carries (only right after the remittance). */
  officerCashAfter: Amount | null;
}

// ---------------------------------------------------------------------------------------------------- susu

export interface SusuFrequency {
  code: string;
  name: string;
  intervalUnit: 'DAY' | 'WEEK' | 'MONTH';
  intervalCount: number;
  active: boolean;
}

export type SusuPlanStatus = 'ACTIVE' | 'COMPLETED' | 'CANCELLED';

export interface SusuPlan {
  id: Uuid;
  planNumber: string;
  customerId: Uuid;
  accountId: Uuid;
  branchId: Uuid;
  frequencyCode: string;
  contributionAmount: Amount;
  currency: string;
  cycleLength: number;
  commissionContributions: number;
  startDate: IsoDate;
  endDate: IsoDate | null;
  targetAmount: Amount | null;
  status: SusuPlanStatus;
  currentCycle: number;
  paid: number;
  missed: number;
  arrears: Amount;
  nextDue: IsoDate | null;
  totalPaid: Amount;
  createdAt: IsoDateTime;
  closedAt: IsoDateTime | null;
  closeReason: string | null;
  version: number;
}

export interface SusuContribution {
  sequenceNo: number;
  cycleNo: number;
  dueDate: IsoDate;
  amount: Amount;
  status: 'EXPECTED' | 'PAID' | 'MISSED' | 'WAIVED';
  paidAt: IsoDateTime | null;
  collectionId: Uuid | null;
  transactionId: Uuid | null;
  waiveReason: string | null;
}

export interface SusuPlanDetail {
  plan: SusuPlan;
  contributions: SusuContribution[];
  commissions: { cycleNo: number; amountDue: Amount; amountCharged: Amount; businessDate: IsoDate }[];
}

// --------------------------------------------------------------------------------------------------- loans

export type InterestMethod = 'FLAT' | 'DECLINING_BALANCE_EQUAL_INSTALLMENT' | 'DECLINING_BALANCE_EQUAL_PRINCIPAL';
export type LoanDayCount = 'ACTUAL_365F' | 'ACTUAL_360' | 'THIRTY_360';
export type RepaymentFrequency = 'DAILY' | 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'QUARTERLY';

/** The terms of a loan product version as sent to the server (GL accounts default to system accounts). */
export interface LoanTermsRequest {
  currency: string;
  minAmount: Amount;
  maxAmount: Amount;
  minInstallments: number;
  maxInstallments: number;
  interestMethod: InterestMethod;
  /** Percent a year. */
  annualRate: string;
  dayCount: LoanDayCount;
  repaymentFrequency: RepaymentFrequency;
  principalGrace?: number;
  interestGrace?: number;
  roundingMode?: 'HALF_EVEN' | 'HALF_UP';
  allocationOrder?: string;
  processingFeeRate?: string;
  processingFeeFlat?: Amount;
  penaltyRate?: string;
  penaltyGraceDays?: number;
  requiredGuarantors?: number;
  collateralCoverage?: string;
  secondApprovalAbove?: Amount;
  requiredKycTier?: string;
}

export interface LoanProductVersion {
  id: Uuid;
  versionNo: number;
  status: 'DRAFT' | 'PUBLISHED' | 'RETIRED';
  currency: string;
  minAmount: Amount;
  maxAmount: Amount;
  minInstallments: number;
  maxInstallments: number;
  interestMethod: InterestMethod;
  annualRate: string;
  dayCount: LoanDayCount;
  repaymentFrequency: RepaymentFrequency;
  principalGrace: number;
  interestGrace: number;
  roundingMode: string;
  allocationOrder: string;
  processingFeeRate: string;
  processingFeeFlat: Amount;
  penaltyRate: string;
  penaltyGraceDays: number;
  requiredGuarantors: number;
  collateralCoverage: string;
  secondApprovalAbove: Amount | null;
  requiredKycTier: string | null;
  createdAt: IsoDateTime;
  publishedAt: IsoDateTime | null;
}

export interface LoanProduct {
  id: Uuid;
  code: string;
  name: string;
  description: string | null;
  status: 'ACTIVE' | 'INACTIVE';
  currentVersion: LoanProductVersion | null;
  versions: LoanProductVersion[];
  version: number;
}

export interface ScheduleLine {
  number: number;
  fromDate: IsoDate;
  dueDate: IsoDate;
  principal: Amount;
  interest: Amount;
  total: Amount;
  outstandingAfter: Amount;
}

/** Computed by the server from the product's terms; never by the browser. */
export interface SchedulePreview {
  principal: Amount;
  processingFee: Amount;
  netDisbursed: Amount;
  totalInterest: Amount;
  totalRepayable: Amount;
  installmentAmount: Amount | null;
  disbursementDate: IsoDate;
  maturityDate: IsoDate;
  lines: ScheduleLine[];
}

export type LoanApplicationStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'ASSESSED'
  | 'RECOMMENDED'
  | 'APPROVED'
  | 'REJECTED'
  | 'WITHDRAWN'
  | 'DISBURSED';

export interface LoanApplication {
  id: Uuid;
  applicationNumber: string;
  customerId: Uuid;
  customerName: string | null;
  branchId: Uuid;
  productId: Uuid;
  productCode: string | null;
  productName: string | null;
  productVersionId: Uuid;
  currency: string;
  requestedAmount: Amount;
  requestedInstallments: number;
  purpose: string;
  monthlyIncome: Amount | null;
  monthlyExpenses: Amount | null;
  existingDebt: Amount | null;
  disbursementAccountId: Uuid;
  status: LoanApplicationStatus;
  riskRating: 'LOW' | 'MEDIUM' | 'HIGH' | null;
  assessmentNote: string | null;
  approvedAmount: Amount | null;
  approvedInstallments: number | null;
  firstDueDate: IsoDate | null;
  loanOfficerId: Uuid;
  loanOfficerName: string | null;
  secondApprovalRequired: boolean;
  loanId: Uuid | null;
  createdAt: IsoDateTime;
  updatedAt: IsoDateTime;
  version: number;
}

export interface LoanWorkflowStep {
  type: 'SUBMIT' | 'ASSESS' | 'RECOMMEND' | 'APPROVE' | 'SECOND_APPROVE' | 'REJECT' | 'WITHDRAW' | 'DISBURSE';
  actorId: Uuid;
  actorName: string | null;
  occurredAt: IsoDateTime;
  note: string | null;
}

export interface LoanGuarantor {
  id: Uuid;
  customerId: Uuid | null;
  fullName: string;
  phone: string | null;
  relationship: string;
  guaranteedAmount: Amount;
  verifiedBy: Uuid | null;
  verifiedAt: IsoDateTime | null;
}

export interface LoanCollateral {
  id: Uuid;
  category: string;
  description: string;
  estimatedValue: Amount;
  forcedSaleValue: Amount;
  valuationDate: IsoDate;
  status: 'PLEDGED' | 'RELEASED';
  verifiedBy: Uuid | null;
  verifiedAt: IsoDateTime | null;
  releasedAt: IsoDateTime | null;
}

export interface LoanApplicationDetail {
  application: LoanApplication;
  steps: LoanWorkflowStep[];
  guarantors: LoanGuarantor[];
  collateral: LoanCollateral[];
  security: { guarantorsRequired: number; guarantorsVerified: number; collateralNeeded: Amount; collateralVerified: Amount };
}

export type LoanStatus = 'ACTIVE' | 'CLOSED' | 'WRITTEN_OFF';

export interface Loan {
  id: Uuid;
  loanNumber: string;
  applicationId: Uuid;
  customerId: Uuid;
  customerName: string | null;
  branchId: Uuid;
  productVersionId: Uuid;
  productCode: string | null;
  productName: string | null;
  repaymentAccountId: Uuid;
  currency: string;
  principal: Amount;
  interestMethod: InterestMethod;
  annualRate: string;
  dayCount: LoanDayCount;
  repaymentFrequency: RepaymentFrequency;
  installments: number;
  processingFee: Amount;
  disbursementDate: IsoDate;
  firstDueDate: IsoDate;
  maturityDate: IsoDate;
  status: LoanStatus;
  daysPastDue: number;
  delinquencyBand: string | null;
  nonAccrual: boolean;
  principalOutstanding: Amount;
  interestReceivable: Amount;
  penaltyReceivable: Amount;
  arrears: Amount;
  nextDueDate: IsoDate | null;
  nextDueAmount: Amount | null;
  provisionHeld: Amount;
  scheduleVersion: number;
  bandFloor: string | null;
  bandFloorUntil: IsoDate | null;
  writtenOff: Amount;
  recovered: Amount;
  closedOn: IsoDate | null;
  version: number;
}

export interface LoanInstallment {
  number: number;
  fromDate: IsoDate;
  dueDate: IsoDate;
  principalDue: Amount;
  interestDue: Amount;
  penaltyDue: Amount;
  principalPaid: Amount;
  interestPaid: Amount;
  penaltyPaid: Amount;
  interestWaived: Amount;
  outstanding: Amount;
  paidOn: IsoDate | null;
  status: 'UPCOMING' | 'DUE' | 'OVERDUE' | 'PAID' | 'PARTLY_PAID';
}

export interface LoanRepayment {
  id: Uuid;
  transactionId: Uuid;
  source: 'ACCOUNT' | 'CASH' | 'FIELD';
  amount: Amount;
  penalty: Amount;
  fee: Amount;
  interest: Amount;
  principal: Amount;
  businessDate: IsoDate;
  receivedBy: Uuid | null;
  createdAt: IsoDateTime;
}

export interface LoanPayoff {
  asOf: IsoDate;
  principal: Amount;
  interest: Amount;
  penalty: Amount;
  total: Amount;
  interestWaived: Amount;
}

export interface LoanRestructureRecord {
  id: Uuid;
  fromVersion: number;
  toVersion: number;
  principal: Amount;
  interestCarried: Amount;
  penaltyCarried: Amount;
  installments: number;
  firstDueDate: IsoDate;
  bandAtRestructure: string | null;
  holdBandDays: number;
  reason: string;
  requestedBy: Uuid;
  approvedBy: Uuid;
  businessDate: IsoDate;
}

export interface LoanRecovery {
  id: Uuid;
  transactionId: Uuid;
  source: 'ACCOUNT' | 'CASH';
  amount: Amount;
  businessDate: IsoDate;
  receivedBy: Uuid | null;
  createdAt: IsoDateTime;
}

export interface LoanDetail {
  loan: Loan;
  schedule: LoanInstallment[];
  repayments: LoanRepayment[];
  payoff: LoanPayoff | null;
  restructures: LoanRestructureRecord[];
  recoveries: LoanRecovery[];
}

export interface RepaymentReceipt {
  repayment: LoanRepayment;
  loan: Loan;
  settled: boolean;
}

export interface RecoveryReceipt {
  recovery: LoanRecovery;
  loan: Loan;
}

export interface LoanCollectionActivity {
  id: Uuid;
  type: 'CALL' | 'VISIT' | 'SMS' | 'LETTER' | 'PROMISE' | 'OTHER';
  note: string;
  daysPastDue: number;
  promisedAmount: Amount | null;
  promisedDate: IsoDate | null;
  promiseStatus: 'OPEN' | 'KEPT' | 'BROKEN' | null;
  businessDate: IsoDate;
  createdBy: Uuid;
  createdAt: IsoDateTime;
}

export interface ArrearsItem {
  loanId: Uuid;
  loanNumber: string;
  customerId: Uuid;
  customerName: string | null;
  customerPhone: string | null;
  branchId: Uuid;
  currency: string;
  daysPastDue: number;
  delinquencyBand: string | null;
  overdue: Amount;
  lastActivity: LoanCollectionActivity | null;
}

export interface DelinquencyBand {
  code: string;
  name: string;
  minDays: number;
  /** Percent of principal outstanding. */
  provisionRate: string;
  suspendAccrual: boolean;
}

export interface LoanPortfolio {
  currency: string;
  activeLoans: number;
  principalOutstanding: Amount;
  portfolioAtRisk30: Amount;
  par30Percent: string;
  provisionHeld: Amount;
  nonAccrualLoans: number;
  bands: { code: string; name: string; loans: number; principalOutstanding: Amount; provisionHeld: Amount }[];
}
