import 'dart:async';

import 'package:banking_core/banking_core.dart';
import 'package:banking_core/testing.dart';
import 'package:field_officer_app/src/app/field_officer_app.dart';
import 'package:field_officer_app/src/app/providers.dart';
import 'package:field_officer_app/src/offline/database_opener.dart';
import 'package:field_officer_app/src/offline/field_database.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final now = DateTime.utc(2026, 10, 8, 10);
  final config = AppConfig.validated(apiBaseUrl: 'https://api.bank.test', releaseMode: true);

  late FakeBackend server;
  late FieldDatabase database;
  final synced = <Map<String, Object?>>[];

  setUp(() {
    synced.clear();
    database = FieldDatabaseOpener.openInMemory();
    server = FakeBackend()
      ..reply('POST', '/api/v1/auth/staff/login', Reply.ok(tokenJson(access: 'access-1', refresh: 'refresh-1', now: now)))
      ..reply('GET', '/api/v1/me', Reply.ok(meJson()))
      ..reply('POST', '/api/v1/field/devices', Reply.ok({'id': 'device-1'}))
      ..reply('GET', '/api/v1/field/me', Reply.ok({
        'officer': {'firstName': 'Kofi', 'currency': 'GHS', 'cashBalance': '0.00', 'maxOfflineAmount': '1000.00', 'maxOfflineHours': 24, 'status': 'ACTIVE'},
      }))
      ..reply('GET', '/api/v1/field/me/customers', Reply.ok([
        {
          'customerId': 'cus-1',
          'customerNumber': 'CUS-1001',
          'displayName': 'Ama Mensah',
          'phone': '+233244000111',
          'accounts': [
            {'accountId': 'acc-1', 'accountNumber': '1000000013', 'title': 'Ama Mensah', 'productType': 'SAVINGS', 'currency': 'GHS', 'status': 'ACTIVE'},
          ],
          'susuPlans': <Object?>[],
        },
      ]))
      // Answers like banking-core: every collection it is sent is posted, under the phone's own reference.
      ..on('POST', '/api/v1/field/sync', (request) async {
        final body = request.data as Map<String, Object?>;
        synced.add(body);
        final collections = (body['collections'] as List).cast<Map<String, Object?>>();
        return Reply.ok({
          'collections': [
            for (final item in collections)
              {'clientReference': item['clientReference'], 'status': 'POSTED', 'transactionReference': 'TXN-20261008-AAAABBBBCCCC', 'balanceAfter': '25.00'},
          ],
          'visits': <Object?>[],
          'highestSequenceNo': collections.length,
          'missing': <Object?>[],
        });
      });
  });

  tearDown(() => database.close());

  testWidgets('collects offline and syncs the collection with its number', (tester) async {
    final backend = await BankingBackend.create(
      config: config,
      endpoints: AuthEndpoints.staff,
      appName: 'field-officer-app',
      keyValueStore: MemoryKeyValueStore(),
      httpAdapter: server,
      clock: () => now,
    );
    unawaited(backend.session.restore());
    await tester.pumpWidget(ProviderScope(
      retry: (_, _) => null,
      overrides: [
        backendProvider.overrideWithValue(backend),
        fieldDatabaseProvider.overrideWithValue(database),
        deviceKeyProvider.overrideWithValue('phone-test-0001'),
      ],
      child: const FieldOfficerApp(),
    ));
    await tester.pumpAndSettle();
    await tester.enterText(find.widgetWithText(TextFormField, 'Institution code'), 'demo-mfi');
    await tester.enterText(find.widgetWithText(TextFormField, 'Username'), 'fieldofficer');
    await tester.enterText(find.widgetWithText(TextFormField, 'Password'), 'Demo@Pass2026');
    await tester.tap(find.widgetWithText(FilledButton, 'Sign in'));
    await tester.pumpAndSettle();

    // The first sync registers the phone and brings the officer's limits and customers.
    await tester.tap(find.text('Sync now'));
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 200)));
    await tester.pumpAndSettle();
    expect(server.calls('POST', '/api/v1/field/devices').single.data, {'deviceKey': 'phone-test-0001', 'name': 'Field phone'});
    expect(find.text('Ama Mensah'), findsOneWidget);

    await tester.tap(find.text('Ama Mensah'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('1000000013 · savings'));
    await tester.pumpAndSettle();
    await tester.enterText(find.widgetWithText(TextField, 'Amount (GHS)'), '25.00');
    await tester.tap(find.text('Save collection'));
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 200)));
    await tester.pumpAndSettle();
    expect(find.textContaining('collection no. 1'), findsOneWidget);

    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(find.text('1 collection (GHS 25.00) waiting to sync'), findsOneWidget);

    await tester.tap(find.text('Sync now'));
    await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 200)));
    await tester.pumpAndSettle();
    expect(find.text('Everything is synced'), findsOneWidget);
    expect(find.text('Synced: 1 accepted.'), findsOneWidget);
    final sent = (synced.last['collections'] as List).single as Map<String, Object?>;
    expect(sent['sequenceNo'], 1);
    expect(sent['amount'], '25.00');
    expect(sent['accountId'], 'acc-1');
    expect(sent['deviceId'] ?? synced.last['deviceId'], 'device-1');

    await tester.tap(find.byTooltip('Collections and visits'));
    await tester.pumpAndSettle();
    expect(find.text('Accepted'), findsOneWidget);
    expect(find.textContaining('TXN-20261008-AAAABBBBCCCC'), findsOneWidget);
  });
}
