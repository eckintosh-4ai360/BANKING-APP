import 'package:banking_api/banking_api.dart';
import 'package:dio/dio.dart';

import '../config/app_config.dart';
import '../errors/api_exception.dart';
import '../util/ids.dart';

/// Typed access to banking-core: unwraps the response envelope, maps every failure to [ApiException] and never
/// retries on its own (a retried write could duplicate a business action; callers decide).
class ApiClient {
  ApiClient(this.dio);

  final Dio dio;

  Future<T> get<T>(String path, T Function(Object? data) parse, {Map<String, Object?>? query}) =>
      _send(() => dio.get<Object?>(path, queryParameters: query), parse);

  /// [idempotencyKey]: required for money movements (Phase 2+); create it once per submission, reuse it on retry.
  ///
  /// [bearerToken] is only for the session's own calls (logout, password change) made on the unauthenticated client.
  Future<T> post<T>(String path, T Function(Object? data) parse, {Object? body, String? idempotencyKey, String? bearerToken}) =>
      _send(() => dio.post<Object?>(path, data: body, options: _options(idempotencyKey, bearerToken)), parse);

  Future<T> put<T>(String path, T Function(Object? data) parse, {Object? body, String? idempotencyKey}) =>
      _send(() => dio.put<Object?>(path, data: body, options: _options(idempotencyKey, null)), parse);

  Future<void> delete(String path) => _send(() => dio.delete<Object?>(path), (_) {});

  Options? _options(String? idempotencyKey, String? bearerToken) {
    if (idempotencyKey == null && bearerToken == null) {
      return null;
    }
    return Options(
      headers: {
        'Idempotency-Key': ?idempotencyKey,
        if (bearerToken != null) 'Authorization': 'Bearer $bearerToken',
      },
    );
  }

  Future<T> _send<T>(Future<Response<Object?>> Function() call, T Function(Object? data) parse) async {
    final Response<Object?> response;
    try {
      response = await call();
    } on DioException catch (error) {
      final cause = error.error;
      if (cause is ApiException) {
        throw cause;
      }
      throw ApiException.fromDio(error);
    }
    final T value;
    try {
      value = unwrapData(response.data, parse);
    } on FormatException {
      throw ApiException.contract();
    }
    return value;
  }
}

/// Adds a fresh `X-Correlation-Id` to each request (matched in backend logs and audit) and the device id.
class RequestMetadataInterceptor extends Interceptor {
  RequestMetadataInterceptor({required this.deviceId, this.appName});

  final String deviceId;
  final String? appName;

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) {
    options.headers['X-Correlation-Id'] = randomUuid();
    options.headers['X-Device-Id'] = deviceId;
    if (appName != null) {
      options.headers['X-Client-App'] = appName;
    }
    handler.next(options);
  }
}

/// A Dio configured for banking-core. Authorised clients add [interceptors] such as the auth interceptor.
Dio createDio(AppConfig config, {required String deviceId, String? appName, List<Interceptor> interceptors = const []}) {
  final dio = Dio(
    BaseOptions(
      baseUrl: config.apiBaseUrl.toString(),
      connectTimeout: const Duration(seconds: 15),
      sendTimeout: const Duration(seconds: 30),
      receiveTimeout: const Duration(seconds: 30),
      responseType: ResponseType.json,
      contentType: Headers.jsonContentType,
      headers: {'Accept': 'application/json'},
      // Redirects could carry the bearer token to another host; the API never redirects.
      followRedirects: false,
    ),
  );
  dio.interceptors.add(RequestMetadataInterceptor(deviceId: deviceId, appName: appName));
  dio.interceptors.addAll(interceptors);
  return dio;
}
