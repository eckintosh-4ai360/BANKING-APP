import 'json.dart';

/// Credentials sent to a login endpoint. Implementations must never include secrets in [toString].
abstract interface class LoginRequest {
  JsonMap toJson();
}

/// Staff sign-in (field officers, tellers, managers): `POST /api/v1/auth/staff/login`.
class StaffLoginRequest implements LoginRequest {
  const StaffLoginRequest({required this.tenantCode, required this.username, required this.password});

  final String tenantCode;
  final String username;
  final String password;

  @override
  JsonMap toJson() => {'tenantCode': tenantCode, 'username': username, 'password': password};

  @override
  String toString() => 'StaffLoginRequest(tenantCode: $tenantCode, username: $username, password: ***)';
}

/// Customer sign-in for the customer app. The backend endpoint arrives with customer digital banking (roadmap
/// phase 6); the contract is fixed here so both sides build against the same shape.
class CustomerLoginRequest implements LoginRequest {
  const CustomerLoginRequest({required this.institutionCode, required this.phoneNumber, required this.password});

  final String institutionCode;
  final String phoneNumber;
  final String password;

  @override
  JsonMap toJson() => {'institutionCode': institutionCode, 'phoneNumber': phoneNumber, 'password': password};

  @override
  String toString() => 'CustomerLoginRequest(institutionCode: $institutionCode, phoneNumber: ***, password: ***)';
}

/// Result of a credential exchange (`TokenResponse` in banking-core). Either a token pair, or an MFA challenge.
class TokenResponse {
  const TokenResponse({
    required this.passwordChangeRequired,
    required this.mfaEnrollmentRequired,
    required this.mfaRequired,
    this.accessToken,
    this.accessTokenExpiresAt,
    this.refreshToken,
    this.refreshTokenExpiresAt,
    this.mfaChallengeToken,
    this.mfaChallengeExpiresAt,
  });

  factory TokenResponse.fromJson(Object? data) {
    final json = asJsonMap(data, 'token response');
    return TokenResponse(
      accessToken: readOptionalString(json, 'accessToken'),
      accessTokenExpiresAt: readOptionalInstant(json, 'accessTokenExpiresAt'),
      refreshToken: readOptionalString(json, 'refreshToken'),
      refreshTokenExpiresAt: readOptionalInstant(json, 'refreshTokenExpiresAt'),
      passwordChangeRequired: readBool(json, 'passwordChangeRequired', fallback: false),
      mfaEnrollmentRequired: readBool(json, 'mfaEnrollmentRequired', fallback: false),
      mfaRequired: readBool(json, 'mfaRequired', fallback: false),
      mfaChallengeToken: readOptionalString(json, 'mfaChallengeToken'),
      mfaChallengeExpiresAt: readOptionalInstant(json, 'mfaChallengeExpiresAt'),
    );
  }

  final String? accessToken;
  final DateTime? accessTokenExpiresAt;
  final String? refreshToken;
  final DateTime? refreshTokenExpiresAt;
  final bool passwordChangeRequired;
  final bool mfaEnrollmentRequired;
  final bool mfaRequired;
  final String? mfaChallengeToken;
  final DateTime? mfaChallengeExpiresAt;

  /// True when the response carries a complete, usable token pair.
  bool get hasTokens =>
      accessToken != null && refreshToken != null && accessTokenExpiresAt != null && refreshTokenExpiresAt != null;

  @override
  String toString() => 'TokenResponse(mfaRequired: $mfaRequired, passwordChangeRequired: $passwordChangeRequired, '
      'mfaEnrollmentRequired: $mfaEnrollmentRequired, tokens: ***)';
}
