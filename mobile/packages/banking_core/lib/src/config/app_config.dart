import 'package:flutter/foundation.dart';

/// Build-time configuration, passed with `--dart-define`:
///
/// - `API_BASE_URL`: banking-core root, e.g. `https://api.bank.example`. Development defaults to the Android
///   emulator's alias for the host machine (`http://10.0.2.2:8080`); use `http://localhost:8080` on iOS simulators.
/// - `INSTITUTION_CODE`: the institution a white-label customer app belongs to (one flavour per institution).
///
/// Release builds refuse plain HTTP: tokens and customer data must never cross the network unencrypted.
class AppConfig {
  const AppConfig._({required this.apiBaseUrl, this.institutionCode});

  factory AppConfig.fromEnvironment() => AppConfig.validated(
        apiBaseUrl: const String.fromEnvironment('API_BASE_URL', defaultValue: 'http://10.0.2.2:8080'),
        institutionCode: const String.fromEnvironment('INSTITUTION_CODE'),
        releaseMode: kReleaseMode,
      );

  /// Validates raw settings; throws [StateError] for anything unsafe or malformed.
  factory AppConfig.validated({required String apiBaseUrl, String? institutionCode, required bool releaseMode}) {
    final uri = Uri.tryParse(apiBaseUrl.trim());
    if (uri == null || !uri.hasScheme || uri.host.isEmpty || (uri.scheme != 'https' && uri.scheme != 'http')) {
      throw StateError('API_BASE_URL must be an absolute http(s) URL');
    }
    if (releaseMode && uri.scheme != 'https') {
      throw StateError('Release builds must use an https API_BASE_URL');
    }
    if (uri.hasQuery || uri.hasFragment || (uri.path.isNotEmpty && uri.path != '/')) {
      throw StateError('API_BASE_URL must be the server root, without a path or query');
    }
    final code = institutionCode?.trim();
    if (code != null && code.isNotEmpty && !institutionCodePattern.hasMatch(code)) {
      throw StateError('INSTITUTION_CODE is not a valid institution code');
    }
    return AppConfig._(
      apiBaseUrl: uri.replace(path: ''),
      institutionCode: code == null || code.isEmpty ? null : code,
    );
  }

  /// Institution (tenant) codes: lower-case letters, digits and hyphens.
  static final institutionCodePattern = RegExp(r'^[a-z0-9][a-z0-9-]{1,31}$');

  final Uri apiBaseUrl;
  final String? institutionCode;
}
