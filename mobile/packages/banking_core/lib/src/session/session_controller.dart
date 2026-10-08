import 'package:banking_api/banking_api.dart';
import 'package:flutter/foundation.dart';

import '../errors/api_exception.dart';
import '../network/api_client.dart';
import '../network/auth_interceptor.dart';
import '../storage/session_store.dart';
import 'session_state.dart';

/// Auth endpoints of one kind of principal.
class AuthEndpoints {
  const AuthEndpoints({
    required this.login,
    this.refresh = '/api/v1/auth/token/refresh',
    this.mfaVerify = '/api/v1/auth/mfa/verify',
    this.logout = '/api/v1/auth/logout',
    this.changePassword = '/api/v1/auth/password',
  });

  /// Staff (field officers and other institution staff).
  static const staff = AuthEndpoints(login: '/api/v1/auth/staff/login');

  /// Customers. Login is the contract for customer digital banking (roadmap phase 6); refresh, MFA, logout and
  /// password change reuse the shared auth endpoints, which identify the principal from the token itself.
  static const customer = AuthEndpoints(login: '/api/v1/customer/auth/login');

  final String login;
  final String refresh;
  final String mfaVerify;
  final String logout;
  final String changePassword;
}

/// Owns the session: credential exchanges, token refresh and sign-out.
///
/// - The access token lives only in memory; the refresh token is in the platform keystore.
/// - Refresh tokens are single-use and the backend revokes the session when one is replayed, so concurrent
///   refreshes share one call (single flight) and every rotation is persisted before it is used.
/// - A rejected refresh token ends the session; a network failure does not (the user may simply be offline).
class SessionController extends ChangeNotifier implements AccessTokenSource {
  SessionController({
    required this._client,
    required this._store,
    required this._endpoints,
    DateTime Function()? clock,
  }) : _clock = clock ?? DateTime.now;

  /// Tokens are refreshed when they have less than this left.
  static const refreshMargin = Duration(seconds: 30);

  static const _expiredNotice = 'Your session has ended. Please sign in again.';

  final ApiClient _client;
  final SessionStore _store;
  final AuthEndpoints _endpoints;
  final DateTime Function() _clock;

  SessionState _state = const SessionRestoring();
  String? _accessToken;
  DateTime? _accessTokenExpiresAt;
  String? _refreshToken;
  DateTime? _refreshTokenExpiresAt;
  String? _institutionCode;
  String? _mfaChallenge;
  DateTime? _mfaChallengeExpiresAt;
  Future<bool>? _refreshing;

  SessionState get state => _state;

  /// The institution the current session belongs to, when known.
  String? get institutionCode => _institutionCode;

  DateTime get _now => _clock().toUtc();

  /// Resumes a stored session at app start.
  Future<void> restore() async {
    final stored = await _store.read();
    if (stored == null || !stored.refreshTokenExpiresAt.isAfter(_now)) {
      await _store.clear();
      _set(const SignedOut());
      return;
    }
    _refreshToken = stored.refreshToken;
    _refreshTokenExpiresAt = stored.refreshTokenExpiresAt;
    _institutionCode = stored.institutionCode;
    try {
      await _refreshOnce();
    } on ApiException catch (error) {
      // Couldn't reach the server: keep the stored session for the next attempt, but don't pretend to be signed in.
      _forgetMemory();
      _set(SignedOut(notice: error.message));
    }
  }

  /// Exchanges credentials; afterwards the state is signed in, MFA required or a restricted session.
  /// Throws [ApiException] with the backend's message (invalid credentials, lockout, rate limit).
  Future<void> signIn(LoginRequest request, {String? institutionCode}) async {
    final tokens = await _client.post(_endpoints.login, TokenResponse.fromJson, body: request.toJson());
    _institutionCode = institutionCode;
    await _apply(tokens);
  }

  Future<void> verifyMfa(String code) async {
    final challenge = _mfaChallenge;
    final expiresAt = _mfaChallengeExpiresAt;
    if (challenge == null || expiresAt == null || !expiresAt.isAfter(_now)) {
      _forgetMemory();
      _set(const SignedOut(notice: 'The verification step expired. Please sign in again.'));
      throw const ApiException(kind: ApiErrorKind.unauthenticated, statusCode: 401, message: 'The verification step expired. Please sign in again.');
    }
    final tokens = await _client.post(_endpoints.mfaVerify, TokenResponse.fromJson, body: {'challengeToken': challenge, 'code': code});
    await _apply(tokens);
  }

  /// Changes the password; the backend ends the other sessions and issues a fresh, unrestricted token pair.
  Future<void> changePassword({required String currentPassword, required String newPassword}) async {
    final token = await validAccessToken();
    if (token == null) {
      throw const ApiException(kind: ApiErrorKind.unauthenticated, statusCode: 401, message: _expiredNotice);
    }
    final tokens = await _client.post(
      _endpoints.changePassword,
      TokenResponse.fromJson,
      body: {'currentPassword': currentPassword, 'newPassword': newPassword},
      bearerToken: token,
    );
    await _apply(tokens);
  }

  /// Signs out: revokes the session on the server when possible and always forgets it locally.
  Future<void> signOut({String? notice}) async {
    final token = _accessToken;
    if (token != null && _accessTokenExpiresAt != null && _accessTokenExpiresAt!.isAfter(_now)) {
      try {
        await _client.post(_endpoints.logout, (_) {}, bearerToken: token);
      } on ApiException {
        // The local session is removed regardless; the server session expires on its own.
      }
    }
    _forgetMemory();
    await _store.clear();
    _set(SignedOut(notice: notice));
  }

  @override
  Future<String?> validAccessToken() async {
    final token = _accessToken;
    final expiresAt = _accessTokenExpiresAt;
    if (token != null && expiresAt != null && expiresAt.isAfter(_now.add(refreshMargin))) {
      return token;
    }
    if (_refreshToken == null) {
      return null;
    }
    return await _refreshOnce() ? _accessToken : null;
  }

  @override
  Future<bool> recoverFromUnauthorized(String? rejectedToken) async {
    if (_accessToken != null && _accessToken != rejectedToken) {
      return true;
    }
    if (_refreshToken == null) {
      return false;
    }
    return _refreshOnce();
  }

  /// One refresh at a time; concurrent callers share the result.
  Future<bool> _refreshOnce() => _refreshing ??= _refresh().whenComplete(() => _refreshing = null);

  Future<bool> _refresh() async {
    final refreshToken = _refreshToken;
    final expiresAt = _refreshTokenExpiresAt;
    if (refreshToken == null || expiresAt == null || !expiresAt.isAfter(_now)) {
      await _expire();
      return false;
    }
    final TokenResponse tokens;
    try {
      tokens = await _client.post(_endpoints.refresh, TokenResponse.fromJson, body: {'refreshToken': refreshToken});
    } on ApiException catch (error) {
      if (error.kind == ApiErrorKind.rejected || error.kind == ApiErrorKind.unauthenticated) {
        await _expire();
        return false;
      }
      rethrow;
    }
    await _apply(tokens);
    return true;
  }

  Future<void> _apply(TokenResponse tokens) async {
    if (tokens.mfaRequired) {
      final challenge = tokens.mfaChallengeToken;
      final expiresAt = tokens.mfaChallengeExpiresAt;
      if (challenge == null || expiresAt == null) {
        throw ApiException.contract();
      }
      _forgetMemory();
      _mfaChallenge = challenge;
      _mfaChallengeExpiresAt = expiresAt;
      _set(MfaRequired(expiresAt: expiresAt));
      return;
    }
    if (!tokens.hasTokens) {
      throw ApiException.contract();
    }
    _accessToken = tokens.accessToken;
    _accessTokenExpiresAt = tokens.accessTokenExpiresAt;
    _refreshToken = tokens.refreshToken;
    _refreshTokenExpiresAt = tokens.refreshTokenExpiresAt;
    _mfaChallenge = null;
    _mfaChallengeExpiresAt = null;
    // Persist the rotated refresh token before anyone can use the new access token: if the app dies now, the
    // stored token is the valid one, not the consumed one.
    await _store.write(StoredSession(
      refreshToken: tokens.refreshToken!,
      refreshTokenExpiresAt: tokens.refreshTokenExpiresAt!,
      institutionCode: _institutionCode,
    ));
    if (_institutionCode != null) {
      await _store.rememberInstitution(_institutionCode!);
    }
    _set(tokens.passwordChangeRequired
        ? const PasswordChangeRequired()
        : tokens.mfaEnrollmentRequired
            ? const MfaEnrollmentRequired()
            : const SignedIn());
  }

  Future<void> _expire() async {
    _forgetMemory();
    await _store.clear();
    _set(const SignedOut(notice: _expiredNotice));
  }

  void _forgetMemory() {
    _accessToken = null;
    _accessTokenExpiresAt = null;
    _refreshToken = null;
    _refreshTokenExpiresAt = null;
    _mfaChallenge = null;
    _mfaChallengeExpiresAt = null;
  }

  void _set(SessionState next) {
    if (next == _state) {
      return;
    }
    _state = next;
    notifyListeners();
  }
}
