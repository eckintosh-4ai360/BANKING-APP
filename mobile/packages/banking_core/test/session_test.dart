import 'package:banking_api/banking_api.dart';
import 'package:banking_core/banking_core.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:banking_core/testing.dart';

void main() {
  final config = AppConfig.validated(apiBaseUrl: 'https://api.bank.test', releaseMode: true);
  const login = StaffLoginRequest(tenantCode: 'demo-mfi', username: 'fieldofficer', password: 'Demo@Pass2026');

  late FakeBackend server;
  late MemoryKeyValueStore storage;
  late DateTime now;

  Future<BankingBackend> backend() => BankingBackend.create(
        config: config,
        endpoints: AuthEndpoints.staff,
        appName: 'field-officer-app',
        keyValueStore: storage,
        httpAdapter: server,
        clock: () => now,
      );

  setUp(() {
    server = FakeBackend();
    storage = MemoryKeyValueStore();
    now = DateTime.utc(2026, 10, 8, 10);
  });

  group('sign-in', () {
    test('signs in, keeps the access token in memory and only the refresh token on disk', () async {
      server.reply('POST', '/api/v1/auth/staff/login', Reply.ok(tokenJson(access: 'access-1', refresh: 'refresh-1', now: now)));
      final app = await backend();

      await app.session.signIn(login, institutionCode: 'demo-mfi');

      expect(app.session.state, const SignedIn());
      final stored = storage.values.values.join(' ');
      expect(stored, contains('refresh-1'));
      expect(stored, isNot(contains('access-1')));
      expect(await app.store.rememberedInstitution(), 'demo-mfi');
      final request = server.calls('POST', '/api/v1/auth/staff/login').single;
      expect(request.data, login.toJson());
      expect(request.headers['X-Correlation-Id'], isNotEmpty);
      expect(request.headers['X-Device-Id'], isNotEmpty);
    });

    test('passes the backend message through on bad credentials', () async {
      server.reply('POST', '/api/v1/auth/staff/login', Reply.error(401, 'INVALID_CREDENTIALS', 'Invalid username or password.'));
      final app = await backend();
      await app.session.restore();

      await expectLater(
        app.session.signIn(login),
        throwsA(isA<ApiException>().having((error) => error.message, 'message', 'Invalid username or password.')),
      );
      expect(app.session.state, isA<SignedOut>());
      expect(storage.values.keys.where((key) => key.contains('session')), isEmpty);
    });

    test('goes through the MFA challenge', () async {
      server
        ..reply('POST', '/api/v1/auth/staff/login', Reply.ok(mfaChallengeJson(now)))
        ..reply('POST', '/api/v1/auth/mfa/verify', Reply.ok(tokenJson(access: 'access-1', refresh: 'refresh-1', now: now)));
      final app = await backend();

      await app.session.signIn(login);
      expect(app.session.state, isA<MfaRequired>());
      expect(storage.values.keys.where((key) => key.contains('session')), isEmpty);

      await app.session.verifyMfa('123456');
      expect(app.session.state, const SignedIn());
      expect(server.calls('POST', '/api/v1/auth/mfa/verify').single.data, {'challengeToken': 'challenge-1', 'code': '123456'});
    });

    test('rejects an expired MFA challenge without calling the server', () async {
      server.reply('POST', '/api/v1/auth/staff/login', Reply.ok(mfaChallengeJson(now)));
      final app = await backend();
      await app.session.signIn(login);

      now = now.add(const Duration(minutes: 6));
      await expectLater(app.session.verifyMfa('123456'), throwsA(isA<ApiException>()));
      expect(app.session.state, isA<SignedOut>());
      expect(server.calls('POST', '/api/v1/auth/mfa/verify'), isEmpty);
    });

    test('a temporary password restricts the session until it is changed', () async {
      server
        ..reply('POST', '/api/v1/auth/staff/login', Reply.ok(tokenJson(access: 'temp', refresh: 'refresh-1', now: now, passwordChangeRequired: true)))
        ..reply('POST', '/api/v1/auth/password', Reply.ok(tokenJson(access: 'access-2', refresh: 'refresh-2', now: now)));
      final app = await backend();

      await app.session.signIn(login);
      expect(app.session.state, const PasswordChangeRequired());

      await app.session.changePassword(currentPassword: 'Temp-123456', newPassword: 'correct horse battery');
      expect(app.session.state, const SignedIn());
      final request = server.calls('POST', '/api/v1/auth/password').single;
      expect(request.headers['Authorization'], 'Bearer temp');
      expect(storage.values.values.join(), contains('refresh-2'));
    });
  });

  group('refresh', () {
    test('resumes a stored session at startup', () async {
      server
        ..reply('POST', '/api/v1/auth/staff/login', Reply.ok(tokenJson(access: 'access-1', refresh: 'refresh-1', now: now)))
        ..reply('POST', '/api/v1/auth/token/refresh', Reply.ok(tokenJson(access: 'access-2', refresh: 'refresh-2', now: now)));
      await (await backend()).session.signIn(login);

      final restarted = await backend();
      await restarted.session.restore();

      expect(restarted.session.state, const SignedIn());
      expect(server.calls('POST', '/api/v1/auth/token/refresh').single.data, {'refreshToken': 'refresh-1'});
      expect(storage.values.values.join(), contains('refresh-2'));
    });

    test('concurrent callers share one refresh, so the single-use token is never replayed', () async {
      server
        ..reply('POST', '/api/v1/auth/staff/login', Reply.ok(tokenJson(access: 'access-1', refresh: 'refresh-1', now: now)))
        ..on('POST', '/api/v1/auth/token/refresh', (_) async {
          await Future<void>.delayed(const Duration(milliseconds: 20));
          return Reply.ok(tokenJson(access: 'access-2', refresh: 'refresh-2', now: now));
        });
      final app = await backend();
      await app.session.signIn(login);
      now = now.add(const Duration(minutes: 10));

      final tokens = await Future.wait([for (var i = 0; i < 5; i++) app.session.validAccessToken()]);

      expect(tokens, everyElement('access-2'));
      expect(server.calls('POST', '/api/v1/auth/token/refresh'), hasLength(1));
    });

    test('a rejected refresh token ends the session and wipes it from the device', () async {
      server
        ..reply('POST', '/api/v1/auth/staff/login', Reply.ok(tokenJson(access: 'access-1', refresh: 'refresh-1', now: now)))
        ..reply('POST', '/api/v1/auth/token/refresh', Reply.error(401, 'INVALID_REFRESH_TOKEN', 'Session revoked'));
      final app = await backend();
      await app.session.signIn(login);
      now = now.add(const Duration(minutes: 11));

      expect(await app.session.validAccessToken(), isNull);
      expect(app.session.state, isA<SignedOut>());
      expect((app.session.state as SignedOut).notice, contains('session has ended'));
      expect(storage.values.keys.where((key) => key.contains('session')), isEmpty);
    });

    test('being offline during a refresh keeps the session', () async {
      server
        ..reply('POST', '/api/v1/auth/staff/login', Reply.ok(tokenJson(access: 'access-1', refresh: 'refresh-1', now: now)))
        ..on('POST', '/api/v1/auth/token/refresh', (request) async {
          throw DioException(requestOptions: request, type: DioExceptionType.connectionError);
        });
      final app = await backend();
      await app.session.signIn(login);
      now = now.add(const Duration(minutes: 11));

      await expectLater(app.session.validAccessToken(), throwsA(isA<ApiException>().having((e) => e.kind, 'kind', ApiErrorKind.network)));
      expect(app.session.state, const SignedIn());
      expect(storage.values.values.join(), contains('refresh-1'));
    });
  });

  group('authorised requests', () {
    test('carry the bearer token', () async {
      server
        ..reply('POST', '/api/v1/auth/staff/login', Reply.ok(tokenJson(access: 'access-1', refresh: 'refresh-1', now: now)))
        ..reply('GET', '/api/v1/me', Reply.ok(meJson()));
      final app = await backend();
      await app.session.signIn(login);

      final me = await app.staff.me();

      expect(me.displayName, 'Kofi Boateng');
      expect(server.calls('GET', '/api/v1/me').single.headers['Authorization'], 'Bearer access-1');
    });

    test('recover once from a 401 by refreshing and retrying', () async {
      var meCalls = 0;
      server
        ..reply('POST', '/api/v1/auth/staff/login', Reply.ok(tokenJson(access: 'access-1', refresh: 'refresh-1', now: now)))
        ..reply('POST', '/api/v1/auth/token/refresh', Reply.ok(tokenJson(access: 'access-2', refresh: 'refresh-2', now: now)))
        ..on('GET', '/api/v1/me', (request) async {
          meCalls++;
          return request.headers['Authorization'] == 'Bearer access-2'
              ? Reply.ok(meJson())
              : Reply.error(401, 'SESSION_REVOKED', 'Token no longer valid');
        });
      final app = await backend();
      await app.session.signIn(login);

      final me = await app.staff.me();

      expect(me.username, 'fieldofficer');
      expect(meCalls, 2);
      expect(server.calls('POST', '/api/v1/auth/token/refresh'), hasLength(1));
    });

    test('do not loop when the retried request is rejected again', () async {
      server
        ..reply('POST', '/api/v1/auth/staff/login', Reply.ok(tokenJson(access: 'access-1', refresh: 'refresh-1', now: now)))
        ..reply('POST', '/api/v1/auth/token/refresh', Reply.ok(tokenJson(access: 'access-2', refresh: 'refresh-2', now: now)))
        ..reply('GET', '/api/v1/me', Reply.error(401, 'SESSION_REVOKED', 'Token no longer valid'));
      final app = await backend();
      await app.session.signIn(login);

      await expectLater(app.staff.me(), throwsA(isA<ApiException>().having((e) => e.kind, 'kind', ApiErrorKind.unauthenticated)));
      expect(server.calls('GET', '/api/v1/me'), hasLength(2));
    });

    test('fail without touching the network when signed out', () async {
      final app = await backend();
      await app.session.restore();

      await expectLater(app.staff.me(), throwsA(isA<ApiException>().having((e) => e.kind, 'kind', ApiErrorKind.unauthenticated)));
      expect(server.requests, isEmpty);
    });

    test('send an idempotency key with state-changing calls when given one', () async {
      server
        ..reply('POST', '/api/v1/auth/staff/login', Reply.ok(tokenJson(access: 'access-1', refresh: 'refresh-1', now: now)))
        ..reply('POST', '/api/v1/collections', Reply.ok({'id': 'c1'}));
      final app = await backend();
      await app.session.signIn(login);
      final key = newIdempotencyKey();

      await app.authorizedClient.post('/api/v1/collections', (data) => data, body: {'amount': '10.00'}, idempotencyKey: key);

      expect(server.calls('POST', '/api/v1/collections').single.headers['Idempotency-Key'], key);
    });
  });

  test('sign-out revokes the server session and forgets everything locally', () async {
    server
      ..reply('POST', '/api/v1/auth/staff/login', Reply.ok(tokenJson(access: 'access-1', refresh: 'refresh-1', now: now)))
      ..reply('POST', '/api/v1/auth/logout', Reply.ok(null));
    final app = await backend();
    await app.session.signIn(login);
    final changes = <SessionState>[];
    app.session.addListener(() => changes.add(app.session.state));

    await app.session.signOut();

    expect(server.calls('POST', '/api/v1/auth/logout').single.headers['Authorization'], 'Bearer access-1');
    expect(app.session.state, const SignedOut());
    expect(changes, [const SignedOut()]);
    expect(await app.session.validAccessToken(), isNull);
    expect(storage.values.keys.where((key) => key.contains('session')), isEmpty);
  });
}
