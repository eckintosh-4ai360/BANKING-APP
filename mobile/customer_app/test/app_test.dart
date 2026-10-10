import 'dart:async';

import 'package:banking_core/banking_core.dart';
import 'package:banking_core/testing.dart';
import 'package:customer_app/src/app/customer_app.dart';
import 'package:customer_app/src/app/providers.dart';
import 'package:customer_app/src/app/router.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

import 'support.dart' show brandingJson, profileJson;

void main() {
  final now = DateTime.utc(2026, 10, 8, 10);
  const brandingPath = '/api/v1/public/institutions/demo-mfi/branding';

  late FakeBackend server;

  setUp(() => server = FakeBackend());

  Future<void> startApp(WidgetTester tester, {String? institution = 'demo-mfi'}) async {
    final config = AppConfig.validated(apiBaseUrl: 'https://api.bank.test', institutionCode: institution, releaseMode: true);
    final backend = await BankingBackend.create(
      config: config,
      endpoints: AuthEndpoints.customer,
      appName: 'customer-app',
      keyValueStore: MemoryKeyValueStore(),
      httpAdapter: server,
      clock: () => now,
    );
    unawaited(backend.session.restore());
    await tester.pumpWidget(
      ProviderScope(
        retry: (_, _) => null,
        overrides: [configProvider.overrideWithValue(config), backendProvider.overrideWithValue(backend)],
        child: const CustomerApp(),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> signIn(WidgetTester tester) async {
    await tester.tap(find.widgetWithText(FilledButton, 'Sign in'));
    await tester.pumpAndSettle();
    await tester.enterText(find.widgetWithText(TextFormField, 'Phone number'), '024 123 4567');
    await tester.enterText(find.widgetWithText(TextFormField, 'Password'), 'correct horse battery');
    await tester.tap(find.widgetWithText(FilledButton, 'Sign in'));
    await tester.pumpAndSettle();
  }

  test('redirectFor keeps signed-out visitors on public screens', () {
    expect(redirectFor(const SignedOut(), Routes.welcome), isNull);
    expect(redirectFor(const SignedOut(), Routes.signIn), isNull);
    expect(redirectFor(const SignedOut(), Routes.activate), isNull);
    expect(redirectFor(const SignedOut(), Routes.signUp), isNull);
    expect(redirectFor(const SignedOut(), Routes.forgotPassword), isNull);
    expect(redirectFor(const SignedOut(), Routes.transfer), Routes.welcome);
    expect(redirectFor(const SignedIn(), Routes.transfer), isNull);
    expect(redirectFor(const SignedIn(), Routes.signUp), Routes.home);
    expect(redirectFor(const SignedOut(), Routes.home), Routes.welcome);
    expect(redirectFor(MfaRequired(expiresAt: now), Routes.home), Routes.verify);
    expect(redirectFor(const PasswordChangeRequired(), Routes.home), Routes.setup);
    expect(redirectFor(const SignedIn(), Routes.welcome), Routes.home);
    expect(redirectFor(const SessionRestoring(), Routes.welcome), Routes.splash);
  });

  testWidgets('starts in the institution’s branding', (tester) async {
    server.reply('GET', brandingPath, Reply.ok(brandingJson()));
    await startApp(tester);

    expect(find.text('Demo Savings & Loans'), findsOneWidget);
    expect(find.text('DS'), findsOneWidget);
    expect(find.widgetWithText(FilledButton, 'Sign in'), findsOneWidget);
    expect(find.text('+233302000000'), findsOneWidget);
    final theme = Theme.of(tester.element(find.text('Mobile banking')));
    expect(theme.colorScheme.primary, const Color(0xFF7A1F5C));
  });

  testWidgets('offers no sign-in when the institution has not enabled mobile banking', (tester) async {
    server.reply('GET', brandingPath, Reply.ok(brandingJson(mobileApp: false)));
    await startApp(tester);

    expect(find.widgetWithText(FilledButton, 'Sign in'), findsNothing);
    expect(find.textContaining('not yet available for Demo Savings & Loans'), findsOneWidget);
  });

  testWidgets('retries the branding when the server could not be reached', (tester) async {
    var attempts = 0;
    server.on('GET', brandingPath, (request) async {
      attempts++;
      if (attempts == 1) {
        throw DioException(requestOptions: request, type: DioExceptionType.connectionError);
      }
      return Reply.ok(brandingJson());
    });
    await startApp(tester);
    expect(find.textContaining('No connection'), findsOneWidget);

    await tester.tap(find.text('Try again'));
    await tester.pumpAndSettle();

    expect(find.text('Demo Savings & Loans'), findsOneWidget);
  });

  testWidgets('explains a build without an institution', (tester) async {
    await startApp(tester, institution: null);
    expect(find.text('This app build is not configured for an institution.'), findsOneWidget);
    expect(find.text('Try again'), findsNothing);
    expect(server.requests, isEmpty);
  });

  testWidgets('signs a customer in with phone and password', (tester) async {
    server
      ..reply('GET', brandingPath, Reply.ok(brandingJson()))
      ..reply('POST', '/api/v1/customer/auth/login', Reply.ok(tokenJson(access: 'access-1', refresh: 'refresh-1', now: now)))
      ..reply('GET', '/api/v1/customer/me', Reply.ok(profileJson()))
      ..reply('GET', '/api/v1/customer/accounts', Reply.ok({'accounts': const <Object?>[], 'totals': const <Object?>[]}))
      ..reply('GET', '/api/v1/customer/onboarding', Reply.error(409, 'NOT_SIGNING_UP', 'You are already a customer.'))
      ..reply('GET', '/api/v1/customer/notifications/unread', Reply.ok({'count': 0}))
      ..reply('POST', '/api/v1/auth/logout', Reply.ok(null));
    await startApp(tester);

    await signIn(tester);

    expect(find.text('Hello, Abena Mensah'), findsOneWidget);
    expect(find.text('You have no accounts yet'), findsOneWidget);
    expect(server.calls('POST', '/api/v1/customer/auth/login').single.data, {
      'institutionCode': 'demo-mfi',
      'phoneNumber': '0241234567',
      'password': 'correct horse battery',
    });

    await tester.tap(find.byTooltip('Sign out'));
    await tester.pumpAndSettle();
    expect(find.text('Mobile banking'), findsOneWidget);
  });

  testWidgets('shows the server’s reason when sign-in is refused', (tester) async {
    server
      ..reply('GET', brandingPath, Reply.ok(brandingJson()))
      ..reply('POST', '/api/v1/customer/auth/login', Reply.error(401, 'INVALID_CREDENTIALS', 'The phone number or password is wrong.'));
    await startApp(tester);

    await signIn(tester);

    expect(find.text('The phone number or password is wrong.'), findsOneWidget);
    expect(find.widgetWithText(TextFormField, 'Password'), findsOneWidget);
  });

  testWidgets('validates the phone number before calling the server', (tester) async {
    server.reply('GET', brandingPath, Reply.ok(brandingJson()));
    await startApp(tester);
    await tester.tap(find.widgetWithText(FilledButton, 'Sign in'));
    await tester.pumpAndSettle();

    await tester.enterText(find.widgetWithText(TextFormField, 'Phone number'), '12');
    await tester.tap(find.widgetWithText(FilledButton, 'Sign in'));
    await tester.pumpAndSettle();

    expect(find.text('Enter a phone number of 7 to 15 digits'), findsOneWidget);
    expect(server.calls('POST', '/api/v1/customer/auth/login'), isEmpty);
  });
}
