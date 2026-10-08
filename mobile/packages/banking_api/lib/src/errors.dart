import 'json.dart';

/// A validation problem reported by the backend for one request field.
class FieldViolation {
  const FieldViolation({required this.field, required this.message});

  factory FieldViolation.fromJson(JsonMap json) =>
      FieldViolation(field: readString(json, 'field'), message: readString(json, 'message'));

  final String field;
  final String message;
}

/// The error envelope of banking-core: `{success:false, code, message, timestamp, traceId, errors[]}`.
class ApiErrorBody {
  const ApiErrorBody({required this.code, required this.message, this.traceId, this.fieldErrors = const []});

  /// Parses an error body; returns null when the payload isn't an error envelope.
  static ApiErrorBody? tryParse(Object? payload) {
    if (payload is! Map) {
      return null;
    }
    final json = asJsonMap(payload);
    final code = json['code'];
    final message = json['message'];
    if (json['success'] != false || code is! String || message is! String) {
      return null;
    }
    return ApiErrorBody(
      code: code,
      message: message,
      traceId: readOptionalString(json, 'traceId'),
      fieldErrors: [for (final item in readObjectList(json, 'errors')) FieldViolation.fromJson(item)],
    );
  }

  final String code;
  final String message;
  final String? traceId;
  final List<FieldViolation> fieldErrors;
}

/// Unwraps the success envelope `{success:true, data}` and parses `data`.
T unwrapData<T>(Object? payload, T Function(Object? data) parse) {
  final json = asJsonMap(payload);
  if (json['success'] != true) {
    throw const FormatException('Expected a success envelope');
  }
  return parse(json['data']);
}
