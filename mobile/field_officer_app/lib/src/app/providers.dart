import 'package:banking_api/banking_api.dart';
import 'package:banking_core/banking_core.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../offline/field_api.dart';
import '../offline/field_database.dart';
import '../offline/field_queue.dart';
import '../offline/sync_engine.dart';

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


// ------------------------------------------------------------------------------------------------ offline work

/// The encrypted queue database, opened once in `main` (and in memory in tests).
final fieldDatabaseProvider = Provider<FieldDatabase>((ref) => throw UnimplementedError('fieldDatabaseProvider must be overridden'));

/// This installation's id: registers the phone with the server.
final deviceKeyProvider = Provider<String>((ref) => throw UnimplementedError('deviceKeyProvider must be overridden'));

final fieldApiProvider = Provider<FieldApi>((ref) => HttpFieldApi(ref.watch(backendProvider).authorizedClient));

final fieldQueueProvider = Provider<FieldQueue>((ref) => FieldQueue(ref.watch(fieldDatabaseProvider)));

final syncEngineProvider = Provider<SyncEngine>((ref) => SyncEngine(
      db: ref.watch(fieldDatabaseProvider),
      api: ref.watch(fieldApiProvider),
      deviceKey: ref.watch(deviceKeyProvider),
      deviceName: 'Field phone',
    ));

final queueSummaryProvider = StreamProvider<QueueSummary>((ref) => ref.watch(fieldQueueProvider).watchSummary());

final customersProvider = StreamProvider<List<MyCustomer>>((ref) => ref.watch(fieldQueueProvider).watchCustomers());

final collectionsProvider = StreamProvider<List<QueuedCollection>>((ref) => ref.watch(fieldQueueProvider).watchCollections());

final visitsProvider = StreamProvider<List<QueuedVisit>>((ref) => ref.watch(fieldQueueProvider).watchVisits());
