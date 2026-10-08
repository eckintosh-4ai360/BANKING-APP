/// Where the user stands with authentication. The app router redirects on every change.
sealed class SessionState {
  const SessionState();

  bool get isSignedIn => this is SignedIn;
}

/// Startup: checking whether a stored session can be resumed.
final class SessionRestoring extends SessionState {
  const SessionRestoring();
}

final class SignedOut extends SessionState {
  const SignedOut({this.notice});

  /// Why the user is signed out (session expired, inactivity), if they didn't choose to.
  final String? notice;

  @override
  bool operator ==(Object other) => other is SignedOut && other.notice == notice;

  @override
  int get hashCode => Object.hash(SignedOut, notice);
}

/// Credentials accepted; an authenticator code is needed before a session exists.
final class MfaRequired extends SessionState {
  const MfaRequired({required this.expiresAt});

  final DateTime expiresAt;

  @override
  bool operator ==(Object other) => other is MfaRequired && other.expiresAt == expiresAt;

  @override
  int get hashCode => Object.hash(MfaRequired, expiresAt);
}

/// Signed in with a temporary password: only the password change is allowed.
final class PasswordChangeRequired extends SessionState {
  const PasswordChangeRequired();

  @override
  bool operator ==(Object other) => other is PasswordChangeRequired;

  @override
  int get hashCode => (PasswordChangeRequired).hashCode;
}

/// Signed in, but the account must enrol an authenticator before using the app.
final class MfaEnrollmentRequired extends SessionState {
  const MfaEnrollmentRequired();

  @override
  bool operator ==(Object other) => other is MfaEnrollmentRequired;

  @override
  int get hashCode => (MfaEnrollmentRequired).hashCode;
}

final class SignedIn extends SessionState {
  const SignedIn();

  @override
  bool operator ==(Object other) => other is SignedIn;

  @override
  int get hashCode => (SignedIn).hashCode;
}
