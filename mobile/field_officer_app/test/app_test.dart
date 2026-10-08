import 'dart:async';

import 'package:banking_core/banking_core.dart';
import 'package:banking_core/testing.dart';
import 'package:field_officer_app/src/app/field_officer_app.dart';
import 'package:field_officer_app/src/app/providers.dart';
import 'package:field_officer_app/src/app/router.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final now = DateTime.utc(2026, 10, 8, 10);
  final config = AppConfig.validated(apiBaseUrl: 'https://api.bank.test', releaseMode: true);

  late FakeBackend server;
  late MemoryKeyValueStore storage;

  setUp(() {
    server = FakeBackend();
    storage = MemoryKeyValueStore();
  });

  Future<BankingBackend> startApp(WidgetTester tester) async {
    final backend = await BankingBackend.create(
      config: config,
      endpoints: AuthEndpoints.staff,
      appName: 'field-officer-app',
      keyValueStore: storage,
      httpAdapter: server,
      clock: () => now,
    );
    // As in main(): restore runs in the background while the splash screen shows; pumping drives it.
    unawaited(backend.session.restore());
    await tester.pumpWidget(
      ProviderScope(
        retry: (_, _) => null,
        overrides: [backendProvider.overrideWithValue(backend)],
        child: const FieldOfficerApp(),
      ),
    );
    await tester.pumpAndSettle();
    return backend;
  }

  Future<void> signIn(WidgetTester tester, {String password = 'Demo@Pass2026'}) async {
    await tester.enterText(find.widgetWithText(TextFormField, 'Institution code'), 'Demo-MFI');
    await tester.enterText(find.widgetWithText(TextFormField, 'Username'), 'fieldofficer');
    await tester.enterText(find.widgetWithText(TextFormField, 'Password'), password);
    await tester.tap(find.widgetWithText(FilledButton, 'Sign in'));
    await tester.pumpAndSettle();
  }

  group('redirectFor', () {
    test('pins each session state to its screen', () {
      expect(redirectFor(const SessionRestoring(), Routes.home), Routes.splash);
      expect(redirectFor(const SignedOut(), Routes.home), Routes.signIn);
      expect(redirectFor(const SignedOut(), Routes.signIn), isNull);
      expect(redirectFor(MfaRequired(expiresAt: now), Routes.home), Routes.mfa);
      expect(redirectFor(const PasswordChangeRequired(), Routes.home), Routes.passwordChange);
      expect(redirectFor(const MfaEnrollmentRequired(), Routes.home), Routes.mfaSetup);
      expect(redirectFor(const SignedIn(), Routes.signIn), Routes.home);
      expect(redirectFor(const SignedIn(), '/home/customers'), isNull);
    });
  });

  testWidgets('signs in and shows the officer home, then signs out', (tester) async {
    server
      ..reply('POST', '/api/v1/auth/staff/login', Reply.ok(tokenJson(access: 'access-1', refresh: 'refresh-1', now: now)))
      ..reply('GET', '/api/v1/me', Reply.ok(meJson()))
      ..reply('POST', '/api/v1/auth/logout', Reply.ok(null));
    await startApp(tester);
    expect(find.text('Sign in with your staff account'), findsOneWidget);

    await signIn(tester);

    expect(find.text('Hello, Kofi'), findsOneWidget);
    expect(find.text('Demo MFI'), findsOneWidget);
    expect(find.text('Field officer'), findsOneWidget);
    expect(find.text('Coming in a later release'), findsNWidgets(4));
    expect(server.calls('POST', '/api/v1/auth/staff/login').single.data, {
      'tenantCode': 'demo-mfi',
      'username': 'fieldofficer',
      'password': 'Demo@Pass2026',
    });

    await tester.tap(find.byTooltip('Sign out'));
    await tester.pumpAndSettle();
    expect(find.text('Sign in with your staff account'), findsOneWidget);
    expect(server.calls('POST', '/api/v1/auth/logout'), hasLength(1));
    // The institution code is remembered for next time; the password is not.
    expect(find.text('demo-mfi'), findsOneWidget);
  });

  testWidgets('shows the backend message for wrong credentials and clears the password', (tester) async {
    server.reply('POST', '/api/v1/auth/staff/login', Reply.error(401, 'INVALID_CREDENTIALS', 'Invalid username or password.'));
    await startApp(tester);

    await signIn(tester, password: 'wrong-password');

    expect(find.text('Invalid username or password.'), findsOneWidget);
    final password = tester.widget<EditableText>(find.descendant(of: find.widgetWithText(TextFormField, 'Password'), matching: find.byType(EditableText)));
    expect(password.controller.text, isEmpty);
  });

  testWidgets('validates the form before calling the server', (tester) async {
    await startApp(tester);
    await tester.tap(find.widgetWithText(FilledButton, 'Sign in'));
    await tester.pumpAndSettle();

    expect(find.text('Enter your institution code'), findsOneWidget);
    expect(find.text('Enter your username'), findsOneWidget);
    expect(find.text('Enter your password'), findsOneWidget);
    expect(server.requests, isEmpty);
  });

  testWidgets('completes MFA before reaching home', (tester) async {
    server
      ..reply('POST', '/api/v1/auth/staff/login', Reply.ok(mfaChallengeJson(now)))
      ..reply('POST', '/api/v1/auth/mfa/verify', Reply.ok(tokenJson(access: 'access-1', refresh: 'refresh-1', now: now)))
      ..reply('GET', '/api/v1/me', Reply.ok(meJson()));
    await startApp(tester);

    await signIn(tester);
    expect(find.text('Enter the 6-digit code from your authenticator app.'), findsOneWidget);

    await tester.enterText(find.byType(TextField), '123456');
    await tester.pumpAndSettle();

    expect(find.text('Hello, Kofi'), findsOneWidget);
    expect(server.calls('POST', '/api/v1/auth/mfa/verify').single.data, {'challengeToken': 'challenge-1', 'code': '123456'});
  });

  testWidgets('forces a temporary password to be replaced', (tester) async {
    server
      ..reply('POST', '/api/v1/auth/staff/login', Reply.ok(tokenJson(access: 'temp', refresh: 'refresh-1', now: now, passwordChangeRequired: true)))
      ..reply('POST', '/api/v1/auth/password', Reply.ok(tokenJson(access: 'access-2', refresh: 'refresh-2', now: now)))
      ..reply('GET', '/api/v1/me', Reply.ok(meJson()));
    await startApp(tester);
    await signIn(tester, password: 'Temp-Password-1');
    expect(find.text('Choose a new password'), findsOneWidget);

    await tester.enterText(find.widgetWithText(TextFormField, 'Current password'), 'Temp-Password-1');
    await tester.enterText(find.widgetWithText(TextFormField, 'New password'), 'short');
    await tester.enterText(find.widgetWithText(TextFormField, 'Confirm new password'), 'short');
    await tester.tap(find.widgetWithText(FilledButton, 'Save and continue'));
    await tester.pumpAndSettle();
    expect(find.text('Use at least 12 characters'), findsOneWidget);
    expect(server.calls('POST', '/api/v1/auth/password'), isEmpty);

    await tester.enterText(find.widgetWithText(TextFormField, 'New password'), 'correct horse battery');
    await tester.enterText(find.widgetWithText(TextFormField, 'Confirm new password'), 'correct horse battery');
    await tester.tap(find.widgetWithText(FilledButton, 'Save and continue'));
    await tester.pumpAndSettle();

    expect(find.text('Hello, Kofi'), findsOneWidget);
    expect(server.calls('POST', '/api/v1/auth/password').single.headers['Authorization'], 'Bearer temp');
  });

  testWidgets('resumes a stored session straight to home', (tester) async {
    server
      ..reply('POST', '/api/v1/auth/token/refresh', Reply.ok(tokenJson(access: 'access-2', refresh: 'refresh-2', now: now)))
      ..reply('GET', '/api/v1/me', Reply.ok(meJson()));
    await SessionStore(storage).write(StoredSession(
      refreshToken: 'refresh-1',
      refreshTokenExpiresAt: now.add(const Duration(hours: 1)),
      institutionCode: 'demo-mfi',
    ));

    await startApp(tester);

    expect(find.text('Hello, Kofi'), findsOneWidget);
  });

  testWidgets('signs out after a period of inactivity', (tester) async {
    server
      ..reply('POST', '/api/v1/auth/staff/login', Reply.ok(tokenJson(access: 'access-1', refresh: 'refresh-1', now: now)))
      ..reply('GET', '/api/v1/me', Reply.ok(meJson()))
      ..reply('POST', '/api/v1/auth/logout', Reply.ok(null));
    await startApp(tester);
    await signIn(tester);
    expect(find.text('Hello, Kofi'), findsOneWidget);

    await tester.pump(inactivityTimeout + const Duration(seconds: 1));
    await tester.pumpAndSettle();

    expect(find.text('You were signed out after a period of inactivity.'), findsOneWidget);
  });
}
