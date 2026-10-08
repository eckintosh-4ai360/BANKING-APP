/// Test doubles for apps and packages built on banking_core. Never import this from production code.
library;

import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';

/// A canned backend reply.
class Reply {
  const Reply(this.status, this.body);

  /// `{success:true, data}`.
  factory Reply.ok(Object? data) => Reply(200, {'success': true, 'message': 'OK', 'data': data, 'timestamp': '2026-10-08T10:00:00Z'});

  factory Reply.error(int status, String code, String message) =>
      Reply(status, {'success': false, 'code': code, 'message': message, 'timestamp': '2026-10-08T10:00:00Z', 'traceId': 'trace-1'});

  final int status;
  final Object? body;
}

typedef Handler = Future<Reply> Function(RequestOptions request);

/// Replaces Dio's network layer: routes `METHOD /path` to handlers and records every request.
class FakeBackend implements HttpClientAdapter {
  final Map<String, Handler> routes = {};
  final List<RequestOptions> requests = [];

  void on(String method, String path, Handler handler) => routes['$method $path'] = handler;

  void reply(String method, String path, Reply reply) => on(method, path, (_) async => reply);

  List<RequestOptions> calls(String method, String path) =>
      requests.where((request) => request.method == method && request.uri.path == path).toList();

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    requests.add(options);
    final handler = routes['${options.method} ${options.uri.path}'];
    if (handler == null) {
      return _json(404, {'success': false, 'code': 'NOT_FOUND', 'message': 'No route ${options.uri.path}'});
    }
    final reply = await handler(options);
    return _json(reply.status, reply.body);
  }

  ResponseBody _json(int status, Object? body) => ResponseBody.fromString(
        jsonEncode(body),
        status,
        headers: {
          Headers.contentTypeHeader: [Headers.jsonContentType],
        },
      );

  @override
  void close({bool force = false}) {}
}

/// Token response the backend sends after a successful exchange.
Map<String, Object?> tokenJson({
  required String access,
  required String refresh,
  required DateTime now,
  Duration accessTtl = const Duration(minutes: 10),
  bool passwordChangeRequired = false,
  bool mfaEnrollmentRequired = false,
}) =>
    {
      'tokenType': 'Bearer',
      'accessToken': access,
      'accessTokenExpiresAt': now.add(accessTtl).toUtc().toIso8601String(),
      'refreshToken': refresh,
      'refreshTokenExpiresAt': now.add(const Duration(hours: 8)).toUtc().toIso8601String(),
      'passwordChangeRequired': passwordChangeRequired,
      'mfaEnrollmentRequired': mfaEnrollmentRequired,
      'mfaRequired': false,
      'mfaChallengeToken': null,
      'mfaChallengeExpiresAt': null,
    };

Map<String, Object?> mfaChallengeJson(DateTime now) => {
      'passwordChangeRequired': false,
      'mfaEnrollmentRequired': false,
      'mfaRequired': true,
      'mfaChallengeToken': 'challenge-1',
      'mfaChallengeExpiresAt': now.add(const Duration(minutes: 5)).toUtc().toIso8601String(),
    };

Map<String, Object?> meJson() => {
      'id': 'staff-1',
      'tenantId': 'tenant-1',
      'tenantCode': 'demo-mfi',
      'institutionName': 'Demo MFI',
      'username': 'fieldofficer',
      'firstName': 'Kofi',
      'lastName': 'Boateng',
      'email': 'kofi@example.test',
      'homeBranchId': 'branch-1',
      'allBranchesAccess': false,
      'roles': [
        {'id': 'r1', 'code': 'FIELD_OFFICER', 'name': 'Field officer'},
      ],
      'permissions': ['customer.view', 'customer.create'],
      'passwordChangeRequired': false,
    };
