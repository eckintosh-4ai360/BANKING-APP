import 'dart:async';

import 'package:banking_core/banking_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'src/app/customer_app.dart';
import 'src/app/providers.dart';

/// One build per institution:
/// `flutter run --dart-define=INSTITUTION_CODE=demo-mfi --dart-define=API_BASE_URL=https://api.example`.
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final config = AppConfig.fromEnvironment();
  final backend = await BankingBackend.create(config: config, endpoints: AuthEndpoints.customer, appName: 'customer-app');
  runApp(
    ProviderScope(
      retry: (_, _) => null,
      overrides: [configProvider.overrideWithValue(config), backendProvider.overrideWithValue(backend)],
      child: const CustomerApp(),
    ),
  );
  unawaited(backend.session.restore());
}
