import 'package:banking_api/banking_api.dart';
import 'package:banking_core/banking_core.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Wired in `main` (and in tests).
final configProvider = Provider<AppConfig>((ref) => throw UnimplementedError('configProvider must be overridden'));

final backendProvider = Provider<BankingBackend>((ref) => throw UnimplementedError('backendProvider must be overridden'));

final sessionProvider = Provider<SessionController>((ref) => ref.watch(backendProvider).session);

final sessionStateProvider = Provider<SessionState>((ref) {
  final session = ref.watch(sessionProvider);
  void changed() => ref.invalidateSelf();
  session.addListener(changed);
  ref.onDispose(() => session.removeListener(changed));
  return session.state;
});

/// Thrown when the build has no institution configured (missing `--dart-define=INSTITUTION_CODE`).
class MissingInstitutionError extends Error {
  @override
  String toString() => 'This app build is not configured for an institution.';
}

/// The institution this white-label build belongs to: name, logo, colours, support contacts and enabled features.
final brandingProvider = FutureProvider<InstitutionBranding>((ref) {
  final code = ref.watch(configProvider).institutionCode;
  if (code == null) {
    throw MissingInstitutionError();
  }
  return ref.watch(backendProvider).institutions.branding(code);
});

/// Feature code the institution switches on (and the platform licenses) for customer self-service.
const mobileAppFeature = 'CUSTOMER_MOBILE_APP';
