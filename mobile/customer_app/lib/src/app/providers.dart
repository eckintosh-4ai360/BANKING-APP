import 'package:banking_api/banking_api.dart';
import 'package:banking_core/banking_core.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../api/customer_api.dart';
import '../api/models.dart';
import '../api/onboarding.dart';

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

final customerApiProvider = Provider<CustomerApi>((ref) {
  final backend = ref.watch(backendProvider);
  return CustomerApi(client: backend.authorizedClient, public: backend.publicClient, session: backend.session);
});

// ------------------------------------------------------------------------------------------ signed-in data

/// The customer's data belongs to one session: everything below is fetched again when a new one starts and never
/// outlives a sign-out.
CustomerApi _signedInApi(Ref ref) {
  final signedIn = ref.watch(sessionStateProvider.select((state) => state.isSignedIn));
  if (!signedIn) {
    throw const ApiException(kind: ApiErrorKind.unauthenticated, message: 'Please sign in to continue.');
  }
  return ref.watch(customerApiProvider);
}

final profileProvider = FutureProvider<CustomerProfile>((ref) => _signedInApi(ref).me());

final accountsProvider = FutureProvider.autoDispose<AccountsOverview>((ref) => _signedInApi(ref).accounts());

/// A statement for an account over a period (the server's default period when both dates are null).
final statementProvider = FutureProvider.autoDispose.family<AccountStatement, ({String accountId, DateTime? from, DateTime? to})>(
  (ref, query) => _signedInApi(ref).statement(query.accountId, from: query.from, to: query.to),
);

final beneficiariesProvider = FutureProvider.autoDispose<List<Beneficiary>>((ref) => _signedInApi(ref).beneficiaries());

final loansProvider = FutureProvider.autoDispose<List<CustomerLoan>>((ref) => _signedInApi(ref).loans());

final loanProvider = FutureProvider.autoDispose.family<LoanDetail, String>((ref, id) => _signedInApi(ref).loan(id));

final susuPlansProvider = FutureProvider.autoDispose<List<SusuPlan>>((ref) => _signedInApi(ref).susuPlans());

final susuPlanProvider = FutureProvider.autoDispose.family<SusuPlanDetail, String>((ref, id) => _signedInApi(ref).susuPlan(id));

final notificationsProvider = FutureProvider.autoDispose<NotificationPage>((ref) => _signedInApi(ref).notifications());

final unreadProvider = FutureProvider.autoDispose<int>((ref) => _signedInApi(ref).unreadNotifications());

final devicesProvider = FutureProvider.autoDispose<List<TrustedDevice>>((ref) => _signedInApi(ref).devices());

final sessionsProvider = FutureProvider.autoDispose<List<SignedInSession>>((ref) => _signedInApi(ref).sessions());

final smsAlertsProvider = FutureProvider.autoDispose<bool>((ref) => _signedInApi(ref).smsAlerts());

/// Signing up in the app. Fails with `NOT_SIGNING_UP` for a customer who did not sign up in the app.
final onboardingProvider = FutureProvider<OnboardingProgress>((ref) => _signedInApi(ref).onboarding());

final idTypesProvider = FutureProvider.autoDispose<List<IdType>>((ref) => _signedInApi(ref).idTypes());
