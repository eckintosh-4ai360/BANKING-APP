import 'package:dio/dio.dart';

import '../errors/api_exception.dart';

/// What the auth interceptor needs from the session.
abstract interface class AccessTokenSource {
  /// A token valid for at least the next few seconds, refreshing first if needed; null when signed out.
  Future<String?> validAccessToken();

  /// Called after the backend rejected [rejectedToken]. Returns true when a newer token is now available.
  Future<bool> recoverFromUnauthorized(String? rejectedToken);
}

/// Adds the bearer token to every request and recovers once from a 401 by refreshing the session.
///
/// Retrying after a 401 is safe for any method: the backend rejects unauthenticated requests before any business
/// logic runs, and state-changing calls also carry an idempotency key.
class AuthInterceptor extends Interceptor {
  AuthInterceptor(this._tokens, this._dio);

  static const _tokenKey = 'banking.authToken';
  static const _retriedKey = 'banking.authRetried';

  final AccessTokenSource _tokens;
  final Dio _dio;

  @override
  Future<void> onRequest(RequestOptions options, RequestInterceptorHandler handler) async {
    final String? token;
    try {
      token = await _tokens.validAccessToken();
    } on ApiException catch (error) {
      handler.reject(DioException(requestOptions: options, error: error, type: DioExceptionType.unknown));
      return;
    }
    if (token == null) {
      handler.reject(
        DioException(
          requestOptions: options,
          error: const ApiException(kind: ApiErrorKind.unauthenticated, statusCode: 401, message: 'Please sign in to continue.'),
          type: DioExceptionType.unknown,
        ),
      );
      return;
    }
    options.headers['Authorization'] = 'Bearer $token';
    options.extra[_tokenKey] = token;
    handler.next(options);
  }

  @override
  Future<void> onError(DioException err, ErrorInterceptorHandler handler) async {
    final options = err.requestOptions;
    if (err.response?.statusCode != 401 || options.extra[_retriedKey] == true) {
      handler.next(err);
      return;
    }
    final bool recovered;
    try {
      recovered = await _tokens.recoverFromUnauthorized(options.extra[_tokenKey] as String?);
    } on ApiException {
      handler.next(err);
      return;
    }
    if (!recovered) {
      handler.next(err);
      return;
    }
    options.extra[_retriedKey] = true;
    try {
      handler.resolve(await _dio.fetch<Object?>(options));
    } on DioException catch (retryError) {
      handler.next(retryError);
    }
  }
}
