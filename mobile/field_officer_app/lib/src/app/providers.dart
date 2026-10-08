import 'package:banking_api/banking_api.dart';
import 'package:banking_core/banking_core.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Wired in `main` (and in tests) with [BankingBackend.create].
final backendProvider = Provider<BankingBackend>((ref) => throw UnimplementedError('backendProvider must be overridden'));

final sessionProvider = Provider<SessionController>((ref) => ref.watch(backendProvider).session);

/// The current [SessionState]; rebuilds dependants whenever the session changes.
final sessionStateProvider = Provider<SessionState>((ref) {
  final session = ref.watch(sessionProvider);
  void changed() => ref.invalidateSelf();
  session.addListener(changed);
  ref.onDispose(() => session.removeListener(changed));
  return session.state;
});

/// The signed-in field officer. Re-fetched whenever a new session starts.
final profileProvider = FutureProvider<StaffProfile>((ref) async {
  final signedIn = ref.watch(sessionStateProvider.select((state) => state.isSignedIn));
  if (!signedIn) {
    throw const ApiException(kind: ApiErrorKind.unauthenticated, message: 'Please sign in to continue.');
  }
  return ref.watch(backendProvider).staff.me();
});

