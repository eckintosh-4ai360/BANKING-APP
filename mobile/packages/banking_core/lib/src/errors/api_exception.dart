import 'package:banking_api/banking_api.dart';
import 'package:dio/dio.dart';

enum ApiErrorKind {
  /// The server answered with a client error (validation, business rule, permission).
  rejected,

  /// The session is missing, expired or revoked (HTTP 401).
  unauthenticated,

  /// The server failed (HTTP 5xx) or answered with something the app doesn't understand.
  server,

  /// No connection could be made.
  network,

  /// The server didn't answer in time.
  timeout,

  /// The request was cancelled by the app.
  cancelled,
}

/// Every failure of an API call, with a message fit for the user. The raw response is never kept, so tokens or
/// personal data in it can't leak into logs through this object.
class ApiException implements Exception {
  const ApiException({
    required this.kind,
    required this.message,
    this.statusCode,
    this.code,
    this.traceId,
    this.fieldErrors = const [],
  });

  factory ApiException.fromDio(DioException error) {
    switch (error.type) {
      case DioExceptionType.connectionTimeout:
      case DioExceptionType.sendTimeout:
      case DioExceptionType.receiveTimeout:
      case DioExceptionType.transformTimeout:
        return const ApiException(kind: ApiErrorKind.timeout, message: 'The server took too long to respond. Please try again.');
      case DioExceptionType.cancel:
        return const ApiException(kind: ApiErrorKind.cancelled, message: 'The request was cancelled.');
      case DioExceptionType.connectionError:
      case DioExceptionType.badCertificate:
        return const ApiException(kind: ApiErrorKind.network, message: 'No connection to the server. Check your internet connection.');
      case DioExceptionType.badResponse:
      case DioExceptionType.unknown:
        final response = error.response;
        if (response == null) {
          return const ApiException(kind: ApiErrorKind.network, message: 'No connection to the server. Check your internet connection.');
        }
        return ApiException.fromResponse(response.statusCode ?? 0, response.data);
    }
  }

  factory ApiException.fromResponse(int statusCode, Object? payload) {
    final body = ApiErrorBody.tryParse(payload);
    if (statusCode >= 500 || body == null && statusCode >= 400 && statusCode != 401) {
      return ApiException(
        kind: ApiErrorKind.server,
        statusCode: statusCode,
        code: body?.code,
        traceId: body?.traceId,
        message: body?.traceId == null
            ? 'Something went wrong on our side. Please try again.'
            : 'Something went wrong on our side. Please try again (reference ${body!.traceId}).',
      );
    }
    if (statusCode == 401) {
      return ApiException(
        kind: ApiErrorKind.unauthenticated,
        statusCode: statusCode,
        code: body?.code ?? 'UNAUTHENTICATED',
        message: body?.message ?? 'Your session has ended. Please sign in again.',
      );
    }
    return ApiException(
      kind: ApiErrorKind.rejected,
      statusCode: statusCode,
      code: body?.code,
      traceId: body?.traceId,
      message: body?.message ?? 'The request could not be completed.',
      fieldErrors: body?.fieldErrors ?? const [],
    );
  }

  /// The response didn't match the API contract.
  factory ApiException.contract() =>
      const ApiException(kind: ApiErrorKind.server, message: 'Unexpected response from the server. Please update the app or try again.');

  final ApiErrorKind kind;
  final String message;
  final int? statusCode;
  final String? code;
  final String? traceId;
  final List<FieldViolation> fieldErrors;

  String? fieldError(String field) {
    for (final violation in fieldErrors) {
      if (violation.field == field) {
        return violation.message;
      }
    }
    return null;
  }

  /// Transient failures worth offering a retry for.
  bool get isTransient => kind == ApiErrorKind.network || kind == ApiErrorKind.timeout || kind == ApiErrorKind.server;

  @override
  String toString() => 'ApiException($kind, status: $statusCode, code: $code, traceId: $traceId)';
}
