import 'package:banking_core/banking_core.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('AppConfig', () {
    test('accepts an https API root and a valid institution', () {
      final config = AppConfig.validated(apiBaseUrl: 'https://api.bank.example/', institutionCode: 'demo-mfi', releaseMode: true);
      expect(config.apiBaseUrl.toString(), 'https://api.bank.example');
      expect(config.institutionCode, 'demo-mfi');
    });

    test('allows plain http only outside release builds', () {
      expect(AppConfig.validated(apiBaseUrl: 'http://10.0.2.2:8080', releaseMode: false).apiBaseUrl.port, 8080);
      expect(() => AppConfig.validated(apiBaseUrl: 'http://api.bank.example', releaseMode: true), throwsStateError);
    });

    test('rejects malformed settings', () {
      for (final url in ['', 'api.bank.example', 'ftp://bank.example', 'https://bank.example/api', 'https://bank.example?x=1']) {
        expect(() => AppConfig.validated(apiBaseUrl: url, releaseMode: false), throwsStateError, reason: url);
      }
      expect(() => AppConfig.validated(apiBaseUrl: 'https://a.example', institutionCode: 'Demo MFI', releaseMode: false), throwsStateError);
      expect(AppConfig.validated(apiBaseUrl: 'https://a.example', institutionCode: '', releaseMode: false).institutionCode, isNull);
    });
  });

  group('ApiException', () {
    RequestOptions request() => RequestOptions(path: '/api/v1/x');

    test('maps validation errors with field messages', () {
      final error = ApiException.fromResponse(400, {
        'success': false,
        'code': 'VALIDATION_FAILED',
        'message': 'Invalid request',
        'errors': [
          {'field': 'username', 'message': 'must not be blank'},
        ],
      });
      expect(error.kind, ApiErrorKind.rejected);
      expect(error.fieldError('username'), 'must not be blank');
      expect(error.isTransient, isFalse);
    });

    test('hides server internals but keeps the trace reference', () {
      final error = ApiException.fromResponse(500, {'success': false, 'code': 'INTERNAL_ERROR', 'message': 'NullPointerException at …', 'traceId': 'abc123'});
      expect(error.kind, ApiErrorKind.server);
      expect(error.message, isNot(contains('NullPointer')));
      expect(error.message, contains('abc123'));
      expect(error.isTransient, isTrue);
    });

    test('maps 401 to unauthenticated and transport failures to network or timeout', () {
      expect(ApiException.fromResponse(401, null).kind, ApiErrorKind.unauthenticated);
      expect(ApiException.fromDio(DioException(requestOptions: request(), type: DioExceptionType.connectionError)).kind, ApiErrorKind.network);
      expect(ApiException.fromDio(DioException(requestOptions: request(), type: DioExceptionType.receiveTimeout)).kind, ApiErrorKind.timeout);
      expect(ApiException.fromResponse(502, '<html>Bad gateway</html>').kind, ApiErrorKind.server);
    });
  });

  test('random UUIDs are version 4 and unique', () {
    final ids = {for (var i = 0; i < 500; i++) randomUuid()};
    expect(ids, hasLength(500));
    expect(ids.first, matches(RegExp(r'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$')));
  });
}
