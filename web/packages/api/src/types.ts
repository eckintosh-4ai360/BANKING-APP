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
