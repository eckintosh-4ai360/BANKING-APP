import 'dart:async';

import 'package:banking_core/banking_core.dart';
import 'package:banking_core/testing.dart';
import 'package:customer_app/src/app/customer_app.dart';
import 'package:customer_app/src/app/providers.dart';
import 'package:customer_app/src/features/onboarding/document_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

const brandingPath = '/api/v1/public/institutions/demo-mfi/branding';

Map<String, Object?> brandingJson({bool mobileApp = true}) => {
      'code': 'demo-mfi',
      'displayName': 'Demo Savings & Loans',
      'logoUrl': null,
      'primaryColor': '#7A1F5C',
      'secondaryColor': '#118844',
      'supportEmail': 'help@demo.test',
      'supportPhone': '+233302000000',
      'baseCurrency': 'GHS',
      'locale': 'en-GH',
      'enabledFeatures': ['SAVINGS', if (mobileApp) 'CUSTOMER_MOBILE_APP'],
    };

Map<String, Object?> profileJson({String status = 'ACTIVE', bool pinLocked = false}) => {
      'customerId': 'customer-1',
      'customerNumber': '0000001234',
      'displayName': 'Abena Mensah',
      'phoneNumber': '+233******567',
      'status': status,
      'kycStatus': status == 'ACTIVE' ? 'VERIFIED' : 'IN_PROGRESS',
      'pinLocked': pinLocked,
    };

Map<String, Object?> accountJson({String id = 'account-1', String number = '1000000016', String title = 'Abena Mensah', String available = '1500.00'}) => {
      'id': id,
      'accountNumber': number,
      'title': title,
      'productName': 'Regular savings',
      'productType': 'SAVINGS',
      'currency': 'GHS',
      'status': 'ACTIVE',
      'ledgerBalance': available,
      'availableBalance': available,
      'openedOn': '2026-01-05',
    };

Map<String, Object?> accountsJson(List<Map<String, Object?>> accounts, {String total = '1500.00'}) => {
      'accounts': accounts,
      'totals': [
        if (accounts.isNotEmpty) {'currency': 'GHS', 'available': total},
      ],
    };

Map<String, Object?> codeSentJson(DateTime now) => {
      'challengeToken': 'tenant-1.challenge-1',
      'sentTo': '+233******567',
      'expiresAt': now.add(const Duration(minutes: 5)).toUtc().toIso8601String(),
    };

/// A backend serving a signed-in customer's home: profile, accounts and an empty inbox.
void serveHome(FakeBackend server, {Map<String, Object?>? profile, List<Map<String, Object?>>? accounts}) {
  server
    ..reply('GET', brandingPath, Reply.ok(brandingJson()))
    ..reply('GET', '/api/v1/customer/me', Reply.ok(profile ?? profileJson()))
    ..reply('GET', '/api/v1/customer/accounts', Reply.ok(accountsJson(accounts ?? [accountJson()])))
    ..reply('GET', '/api/v1/customer/onboarding', Reply.error(409, 'NOT_SIGNING_UP', 'You are already a customer.'))
    ..reply('GET', '/api/v1/customer/notifications/unread', Reply.ok({'count': 0}))
    ..reply('GET', '/api/v1/customer/notifications', Reply.ok({'items': const <Object?>[], 'page': 0, 'size': 30, 'totalItems': 0, 'totalPages': 0}))
    ..reply('GET', '/api/v1/customer/beneficiaries', Reply.ok(const <Object?>[]))
    ..reply('POST', '/api/v1/auth/logout', Reply.ok(null));
}

/// Starts the app against [server]; with [signedIn], a stored session is resumed through a refresh.
Future<BankingBackend> startApp(
  WidgetTester tester,
  FakeBackend server, {
  required DateTime now,
  bool signedIn = false,
  DocumentPicker? picker,
}) async {
  final config = AppConfig.validated(apiBaseUrl: 'https://api.bank.test', institutionCode: 'demo-mfi', releaseMode: true);
  final storage = MemoryKeyValueStore();
  if (signedIn) {
    await SessionStore(storage).write(StoredSession(
      refreshToken: 'c.tenant.refresh-0',
      refreshTokenExpiresAt: now.add(const Duration(hours: 8)),
      institutionCode: 'demo-mfi',
    ));
    server.reply('POST', '/api/v1/auth/token/refresh', Reply.ok(tokenJson(access: 'access-1', refresh: 'c.tenant.refresh-1', now: now)));
  }
  final backend = await BankingBackend.create(
    config: config,
    endpoints: AuthEndpoints.customer,
    appName: 'customer-app',
    keyValueStore: storage,
    httpAdapter: server,
    clock: () => now,
  );
  unawaited(backend.session.restore());
  await tester.pumpWidget(
    ProviderScope(
      retry: (_, _) => null,
      overrides: [
        configProvider.overrideWithValue(config),
        backendProvider.overrideWithValue(backend),
        if (picker != null) documentPickerProvider.overrideWithValue(picker),
      ],
      child: const CustomerApp(),
    ),
  );
  await tester.pumpAndSettle();
  return backend;
}

/// Types a PIN into the PIN dialog and confirms.
Future<void> enterPin(WidgetTester tester, String pin) async {
  await tester.enterText(find.descendant(of: find.byType(AlertDialog), matching: find.byType(TextFormField)), pin);
  await tester.tap(find.widgetWithText(FilledButton, 'Confirm'));
  await tester.pumpAndSettle();
}
