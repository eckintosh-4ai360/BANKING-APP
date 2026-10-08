import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../features/auth/setup_screen.dart';
import '../features/auth/sign_in_screen.dart';
import '../features/auth/verify_screen.dart';
import '../features/home/home_screen.dart';
import '../features/welcome/welcome_screen.dart';

abstract final class Routes {
  static const splash = '/splash';
  static const welcome = '/';
  static const signIn = '/sign-in';
  static const verify = '/sign-in/verify';
  static const setup = '/setup';
  static const home = '/home';
}

/// Signed-out visitors may browse the welcome and sign-in screens; every other state is pinned to its screen.
String? redirectFor(SessionState state, String location) {
  final target = switch (state) {
    SessionRestoring() => Routes.splash,
    SignedOut() => location == Routes.welcome || location == Routes.signIn ? location : Routes.welcome,
    MfaRequired() => Routes.verify,
    PasswordChangeRequired() || MfaEnrollmentRequired() => Routes.setup,
    SignedIn() => location.startsWith(Routes.home) ? location : Routes.home,
  };
  return target == location ? null : target;
}

GoRouter createRouter(SessionController session) => GoRouter(
      initialLocation: Routes.splash,
      refreshListenable: session,
      redirect: (context, state) => redirectFor(session.state, state.matchedLocation),
      routes: [
        GoRoute(path: Routes.splash, builder: (_, _) => const Scaffold(body: LoadingView())),
        GoRoute(path: Routes.welcome, builder: (_, _) => const WelcomeScreen()),
        GoRoute(path: Routes.signIn, builder: (_, _) => const SignInScreen()),
        GoRoute(path: Routes.verify, builder: (_, _) => const VerifyScreen()),
        GoRoute(path: Routes.setup, builder: (_, _) => const SetupScreen()),
        GoRoute(path: Routes.home, builder: (_, _) => const HomeScreen()),
      ],
    );
