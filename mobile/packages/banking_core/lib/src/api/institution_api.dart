import 'package:banking_api/banking_api.dart';

import '../config/app_config.dart';
import '../errors/api_exception.dart';
import '../network/api_client.dart';

/// Public, unauthenticated institution data used before sign-in (white-label bootstrap).
class InstitutionApi {
  InstitutionApi(this._client);

  final ApiClient _client;

  Future<InstitutionBranding> branding(String institutionCode) {
    if (!AppConfig.institutionCodePattern.hasMatch(institutionCode)) {
      throw const ApiException(kind: ApiErrorKind.rejected, message: 'Unknown institution code.');
    }
    return _client.get('/api/v1/public/institutions/$institutionCode/branding', InstitutionBranding.fromJson);
  }
}

/// Endpoints for signed-in staff.
class StaffApi {
  StaffApi(this._client);

  final ApiClient _client;

  /// The signed-in staff member, their roles and permissions.
  Future<StaffProfile> me() => _client.get('/api/v1/me', StaffProfile.fromJson);
}
