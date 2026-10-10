import 'package:banking_api/banking_api.dart';

/// Signing up in the app (`/api/v1/customer/onboarding`): the stages, the KYC evidence the institution asks for,
/// the review, and what the person has entered so far.

DateTime? _date(JsonMap json, String key) {
  final value = readOptionalString(json, key);
  if (value == null) {
    return null;
  }
  final parsed = DateTime.tryParse(value);
  if (parsed == null) {
    throw FormatException('Expected "$key" to be a date');
  }
  return DateTime.utc(parsed.year, parsed.month, parsed.day);
}

JsonMap? _optionalObject(JsonMap json, String key) => json[key] == null ? null : asJsonMap(json[key], key);

class OnboardingStage {
  const OnboardingStage({required this.code, required this.title, required this.required, required this.state});

  factory OnboardingStage.fromJson(JsonMap json) => OnboardingStage(
        code: readString(json, 'code'),
        title: readString(json, 'title'),
        required: readBool(json, 'required'),
        state: readString(json, 'state'),
      );

  final String code;
  final String title;

  /// Whether the institution asks for it (a signature, for example, only where its KYC tier needs one).
  final bool required;

  /// DONE, TO_DO, IN_REVIEW, ACTION_NEEDED or NOT_APPROVED.
  final String state;

  bool get done => state == 'DONE';
}

class KycRequirement {
  const KycRequirement({required this.code, required this.description, required this.captured, required this.accepted});

  factory KycRequirement.fromJson(JsonMap json) => KycRequirement(
        code: readString(json, 'code'),
        description: readString(json, 'description'),
        captured: readBool(json, 'captured'),
        accepted: readBool(json, 'accepted'),
      );

  final String code;
  final String description;
  final bool captured;
  final bool accepted;
}

class OnboardingReview {
  const OnboardingReview({required this.status, this.submittedAt, this.note});

  factory OnboardingReview.fromJson(JsonMap json) => OnboardingReview(
        status: readString(json, 'status'),
        submittedAt: readOptionalInstant(json, 'submittedAt'),
        note: readOptionalString(json, 'note'),
      );

  final String status;
  final DateTime? submittedAt;

  /// What the reviewer asked to correct, while the details are returned for correction.
  final String? note;
}

class OnboardingProfile {
  const OnboardingProfile({
    this.title,
    this.firstName,
    this.middleName,
    this.lastName,
    this.dateOfBirth,
    this.gender,
    this.nationality,
    this.maritalStatus,
    this.email,
    this.employmentStatus,
    this.occupation,
    this.employerName,
    this.monthlyIncomeBand,
  });

  factory OnboardingProfile.fromJson(JsonMap json) => OnboardingProfile(
        title: readOptionalString(json, 'title'),
        firstName: readOptionalString(json, 'firstName'),
        middleName: readOptionalString(json, 'middleName'),
        lastName: readOptionalString(json, 'lastName'),
        dateOfBirth: _date(json, 'dateOfBirth'),
        gender: readOptionalString(json, 'gender'),
        nationality: readOptionalString(json, 'nationality'),
        maritalStatus: readOptionalString(json, 'maritalStatus'),
        email: readOptionalString(json, 'email'),
        employmentStatus: readOptionalString(json, 'employmentStatus'),
        occupation: readOptionalString(json, 'occupation'),
        employerName: readOptionalString(json, 'employerName'),
        monthlyIncomeBand: readOptionalString(json, 'monthlyIncomeBand'),
      );

  final String? title;
  final String? firstName;
  final String? middleName;
  final String? lastName;
  final DateTime? dateOfBirth;
  final String? gender;
  final String? nationality;
  final String? maritalStatus;
  final String? email;
  final String? employmentStatus;
  final String? occupation;
  final String? employerName;
  final String? monthlyIncomeBand;
}

class OnboardingAddress {
  const OnboardingAddress({required this.line1, this.line2, this.city, this.district, this.region, this.digitalAddress, this.landmark});

  factory OnboardingAddress.fromJson(JsonMap json) => OnboardingAddress(
        line1: readString(json, 'line1'),
        line2: readOptionalString(json, 'line2'),
        city: readOptionalString(json, 'city'),
        district: readOptionalString(json, 'district'),
        region: readOptionalString(json, 'region'),
        digitalAddress: readOptionalString(json, 'digitalAddress'),
        landmark: readOptionalString(json, 'landmark'),
      );

  final String line1;
  final String? line2;
  final String? city;
  final String? district;
  final String? region;
  final String? digitalAddress;
  final String? landmark;
}

class OnboardingNextOfKin {
  const OnboardingNextOfKin({required this.fullName, required this.relationship, this.phone, this.email, this.address});

  factory OnboardingNextOfKin.fromJson(JsonMap json) => OnboardingNextOfKin(
        fullName: readString(json, 'fullName'),
        relationship: readString(json, 'relationship'),
        phone: readOptionalString(json, 'phone'),
        email: readOptionalString(json, 'email'),
        address: readOptionalString(json, 'address'),
      );

  final String fullName;
  final String relationship;
  final String? phone;
  final String? email;
  final String? address;
}

class OnboardingIdentification {
  const OnboardingIdentification({required this.idTypeCode, required this.idNumberMasked, this.expiryDate, this.verificationStatus});

  factory OnboardingIdentification.fromJson(JsonMap json) => OnboardingIdentification(
        idTypeCode: readString(json, 'idTypeCode'),
        idNumberMasked: readString(json, 'idNumberMasked'),
        expiryDate: _date(json, 'expiryDate'),
        verificationStatus: readOptionalString(json, 'verificationStatus'),
      );

  final String idTypeCode;

  /// The server never returns the full number.
  final String idNumberMasked;
  final DateTime? expiryDate;
  final String? verificationStatus;
}

class OnboardingDocument {
  const OnboardingDocument({required this.id, required this.documentType, required this.reviewStatus});

  factory OnboardingDocument.fromJson(JsonMap json) => OnboardingDocument(
        id: readString(json, 'id'),
        documentType: readString(json, 'documentType'),
        reviewStatus: readString(json, 'reviewStatus'),
      );

  final String id;
  final String documentType;

  /// PENDING until staff review it, then ACCEPTED or REJECTED (a rejected one is uploaded again).
  final String reviewStatus;
}

class RiskProfile {
  const RiskProfile({required this.sourceOfFunds, required this.accountPurpose, required this.expectedMonthlyTurnover, required this.politicallyExposed});

  factory RiskProfile.fromJson(JsonMap json) => RiskProfile(
        sourceOfFunds: readString(json, 'sourceOfFunds'),
        accountPurpose: readString(json, 'accountPurpose'),
        expectedMonthlyTurnover: readString(json, 'expectedMonthlyTurnover'),
        politicallyExposed: readBool(json, 'politicallyExposed'),
      );

  final String sourceOfFunds;
  final String accountPurpose;
  final String expectedMonthlyTurnover;
  final bool politicallyExposed;

  JsonMap toJson() => {
        'sourceOfFunds': sourceOfFunds,
        'accountPurpose': accountPurpose,
        'expectedMonthlyTurnover': expectedMonthlyTurnover,
        'politicallyExposed': politicallyExposed,
      };
}

class OpenedAccount {
  const OpenedAccount({required this.id, required this.accountNumber, required this.productName, required this.status});

  factory OpenedAccount.fromJson(JsonMap json) => OpenedAccount(
        id: readString(json, 'id'),
        accountNumber: readString(json, 'accountNumber'),
        productName: readString(json, 'productName'),
        status: readString(json, 'status'),
      );

  final String id;
  final String accountNumber;
  final String productName;
  final String status;
}

class OnboardingProgress {
  const OnboardingProgress({
    required this.customerNumber,
    required this.status,
    required this.kycStatus,
    required this.stages,
    required this.requirements,
    required this.profile,
    required this.documents,
    this.nextStage,
    this.review,
    this.address,
    this.nextOfKin,
    this.identification,
    this.riskProfile,
    this.account,
  });

  factory OnboardingProgress.fromJson(Object? data) {
    final json = asJsonMap(data, 'sign-up');
    final review = _optionalObject(json, 'review');
    final address = _optionalObject(json, 'address');
    final nextOfKin = _optionalObject(json, 'nextOfKin');
    final identification = _optionalObject(json, 'identification');
    final riskProfile = _optionalObject(json, 'riskProfile');
    final account = _optionalObject(json, 'account');
    return OnboardingProgress(
      customerNumber: readString(json, 'customerNumber'),
      status: readString(json, 'status'),
      kycStatus: readString(json, 'kycStatus'),
      nextStage: readOptionalString(json, 'nextStage'),
      stages: readObjectList(json, 'stages').map(OnboardingStage.fromJson).toList(),
      requirements: readObjectList(json, 'requirements').map(KycRequirement.fromJson).toList(),
      review: review == null ? null : OnboardingReview.fromJson(review),
      profile: OnboardingProfile.fromJson(_optionalObject(json, 'profile') ?? const {}),
      address: address == null ? null : OnboardingAddress.fromJson(address),
      nextOfKin: nextOfKin == null ? null : OnboardingNextOfKin.fromJson(nextOfKin),
      identification: identification == null ? null : OnboardingIdentification.fromJson(identification),
      documents: readObjectList(json, 'documents').map(OnboardingDocument.fromJson).toList(),
      riskProfile: riskProfile == null ? null : RiskProfile.fromJson(riskProfile),
      account: account == null ? null : OpenedAccount.fromJson(account),
    );
  }

  final String customerNumber;
  final String status;
  final String kycStatus;

  /// The stage to do next; null once done, or when the sign-up was not approved.
  final String? nextStage;
  final List<OnboardingStage> stages;
  final List<KycRequirement> requirements;
  final OnboardingReview? review;
  final OnboardingProfile profile;
  final OnboardingAddress? address;
  final OnboardingNextOfKin? nextOfKin;
  final OnboardingIdentification? identification;
  final List<OnboardingDocument> documents;
  final RiskProfile? riskProfile;
  final OpenedAccount? account;

  OnboardingStage? stage(String code) => stages.where((stage) => stage.code == code).firstOrNull;

  /// The latest upload of a document type, if any.
  OnboardingDocument? document(String type) => documents.where((document) => document.documentType == type).lastOrNull;

  /// Waiting for staff: nothing to change in the meantime.
  bool get inReview => stage('COMPLIANCE')?.state == 'IN_REVIEW';

  bool get approved => stage('COMPLIANCE')?.state == 'DONE';
}

/// An identity document the institution accepts.
class IdType {
  const IdType({required this.code, required this.name, required this.requiresExpiry, this.formatHint});

  factory IdType.fromJson(JsonMap json) => IdType(
        code: readString(json, 'code'),
        name: readString(json, 'name'),
        formatHint: readOptionalString(json, 'formatHint'),
        requiresExpiry: readBool(json, 'requiresExpiry', fallback: false),
      );

  final String code;
  final String name;
  final String? formatHint;
  final bool requiresExpiry;
}
