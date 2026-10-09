import 'dart:async';

import 'package:banking_core/banking_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'src/app/field_officer_app.dart';
import 'src/app/providers.dart';
import 'src/offline/database_opener.dart';

/// Run with `flutter run --dart-define=API_BASE_URL=https://api.example` (development defaults to the Android
/// emulator's address of the host machine).
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final keys = SecureKeyValueStore();
  final backend = await BankingBackend.create(
    config: AppConfig.fromEnvironment(),
    endpoints: AuthEndpoints.staff,
    appName: 'field-officer-app',
    keyValueStore: keys,
  );
  // Collections taken offline live in an encrypted database whose key stays in the platform keystore.
  final database = await FieldDatabaseOpener.open(keys);
  final deviceKey = await backend.store.deviceId();
  runApp(
    ProviderScope(
      // Failed loads are retried when the user asks, not silently in the background.
      retry: (_, _) => null,
      overrides: [
        backendProvider.overrideWithValue(backend),
        fieldDatabaseProvider.overrideWithValue(database),
        deviceKeyProvider.overrideWithValue(deviceKey),
      ],
      child: const FieldOfficerApp(),
    ),
  );
  unawaited(backend.session.restore());
}
