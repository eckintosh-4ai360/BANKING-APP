import 'dart:math';

final Random _secureRandom = Random.secure();

/// A random (version 4) UUID from a cryptographically secure source.
String randomUuid() {
  final bytes = List<int>.generate(16, (_) => _secureRandom.nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  final hex = bytes.map((byte) => byte.toRadixString(16).padLeft(2, '0')).join();
  return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-'
      '${hex.substring(16, 20)}-${hex.substring(20)}';
}

/// Idempotency key for a state-changing request. Create one per user submission and reuse it when retrying that
/// same submission, so the backend processes it once even if the first response was lost.
String newIdempotencyKey() => randomUuid();
