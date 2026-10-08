import 'dart:convert';

import '../util/ids.dart';
import 'key_value_store.dart';

/// What survives an app restart: the refresh token (never the access token, which lives in memory only).
class StoredSession {
  const StoredSession({required this.refreshToken, required this.refreshTokenExpiresAt, this.institutionCode});

  final String refreshToken;
  final DateTime refreshTokenExpiresAt;
  final String? institutionCode;

  @override
  String toString() => 'StoredSession(institutionCode: $institutionCode, refreshToken: ***)';
}

/// Persists the session and small non-secret settings in the secure store.
class SessionStore {
  SessionStore(this._store);

  static const _sessionKey = 'banking.session.v1';
  static const _deviceKey = 'banking.device.v1';
  static const _institutionKey = 'banking.institution.v1';

  final KeyValueStore _store;

  Future<StoredSession?> read() async {
    final raw = await _store.read(_sessionKey);
    if (raw == null) {
      return null;
    }
    try {
      final json = jsonDecode(raw) as Map<String, Object?>;
      final token = json['refreshToken'] as String?;
      final expiresAt = DateTime.tryParse(json['refreshTokenExpiresAt'] as String? ?? '');
      if (token == null || expiresAt == null) {
        await clear();
        return null;
      }
      return StoredSession(refreshToken: token, refreshTokenExpiresAt: expiresAt.toUtc(), institutionCode: json['institutionCode'] as String?);
    } on Object {
      // Unreadable entry (e.g. an older format): drop it; the user signs in again.
      await clear();
      return null;
    }
  }

  Future<void> write(StoredSession session) => _store.write(
        _sessionKey,
        jsonEncode({
          'refreshToken': session.refreshToken,
          'refreshTokenExpiresAt': session.refreshTokenExpiresAt.toUtc().toIso8601String(),
          'institutionCode': session.institutionCode,
        }),
      );

  Future<void> clear() => _store.delete(_sessionKey);

  /// A random id per installation, sent as `X-Device-Id` so the audit trail can tell devices apart.
  Future<String> deviceId() async {
    final existing = await _store.read(_deviceKey);
    if (existing != null && existing.isNotEmpty) {
      return existing;
    }
    final created = randomUuid();
    await _store.write(_deviceKey, created);
    return created;
  }

  /// The institution code last used to sign in (a convenience, not a secret).
  Future<String?> rememberedInstitution() => _store.read(_institutionKey);

  Future<void> rememberInstitution(String code) => _store.write(_institutionKey, code);
}
