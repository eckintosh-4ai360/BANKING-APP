/// Strict readers for JSON maps. A missing or mistyped field raises a [FormatException] naming the field, so a
/// contract mismatch with the backend fails loudly instead of producing half-filled objects.
typedef JsonMap = Map<String, Object?>;

JsonMap asJsonMap(Object? value, [String context = 'response']) {
  if (value is Map<String, Object?>) {
    return value;
  }
  if (value is Map) {
    return value.map((key, item) => MapEntry(key.toString(), item));
  }
  throw FormatException('Expected an object for $context');
}

String readString(JsonMap json, String key) {
  final value = json[key];
  if (value is String) {
    return value;
  }
  throw FormatException('Expected "$key" to be a string');
}

String? readOptionalString(JsonMap json, String key) {
  final value = json[key];
  if (value == null || value is String) {
    return value as String?;
  }
  throw FormatException('Expected "$key" to be a string or null');
}

bool readBool(JsonMap json, String key, {bool? fallback}) {
  final value = json[key];
  if (value is bool) {
    return value;
  }
  if (value == null && fallback != null) {
    return fallback;
  }
  throw FormatException('Expected "$key" to be a boolean');
}

DateTime? readOptionalInstant(JsonMap json, String key) {
  final value = readOptionalString(json, key);
  if (value == null) {
    return null;
  }
  final parsed = DateTime.tryParse(value);
  if (parsed == null) {
    throw FormatException('Expected "$key" to be an ISO-8601 instant');
  }
  return parsed.toUtc();
}

List<String> readStringList(JsonMap json, String key) {
  final value = json[key];
  if (value == null) {
    return const [];
  }
  if (value is List && value.every((item) => item is String)) {
    return List.unmodifiable(value.cast<String>());
  }
  throw FormatException('Expected "$key" to be a list of strings');
}

List<JsonMap> readObjectList(JsonMap json, String key) {
  final value = json[key];
  if (value == null) {
    return const [];
  }
  if (value is List) {
    return [for (final item in value) asJsonMap(item, key)];
  }
  throw FormatException('Expected "$key" to be a list');
}
