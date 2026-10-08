import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../features/auth/mfa_screen.dart';
import '../features/auth/password_change_screen.dart';
import '../features/auth/setup_required_screen.dart';
import '../features/auth/sign_in_screen.dart';
import '../features/home/home_screen.dart';

abstract final class Routes {
  static const splash = '/';
  static const signIn = '/sign-in';
  static const mfa = '/sign-in/mfa';
  static const passwordChange = '/setup/password';
  static const mfaSetup = '/setup/mfa';
  static const home = '/home';
}

/// Where the session state allows the user to be. Signed-in users may go anywhere under /home; everyone else is
/// pinned to the one screen their state calls for (no deep link can skip sign-in, MFA or a forced password change).
String? redirectFor(SessionState state, String location) {
  final target = switch (state) {
    SessionRestoring() => Routes.splash,
    SignedOut() => Routes.signIn,
    MfaRequired() => Routes.mfa,
    PasswordChangeRequired() => Routes.passwordChange,
    MfaEnrollmentRequired() => Routes.mfaSetup,
    SignedIn() => location.startsWith(Routes.home) ? location : Routes.home,
  };
  return target == location ? null : target;
}

GoRouter createRouter(SessionController session) => GoRouter(
      initialLocation: Routes.splash,
      refreshListenable: session,
      redirect: (context, state) => redirectFor(session.state, state.matchedLocation),
      routes: [
        GoRoute(path: Routes.splash, builder: (_, _) => const Scaffold(body: LoadingView(message: 'Starting…'))),
        GoRoute(path: Routes.signIn, builder: (_, _) => const SignInScreen()),
        GoRoute(path: Routes.mfa, builder: (_, _) => const MfaScreen()),
        GoRoute(path: Routes.passwordChange, builder: (_, _) => const PasswordChangeScreen()),
        GoRoute(path: Routes.mfaSetup, builder: (_, _) => const SetupRequiredScreen()),
        GoRoute(path: Routes.home, builder: (_, _) => const HomeScreen()),
      ],
    );
