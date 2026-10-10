import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../features/accounts/account_screen.dart';
import '../features/auth/activation_screen.dart';
import '../features/auth/forgot_password_screen.dart';
import '../features/auth/setup_screen.dart';
import '../features/auth/sign_in_screen.dart';
import '../features/auth/sign_up_screen.dart';
import '../features/auth/verify_screen.dart';
import '../features/home/home_screen.dart';
import '../features/onboarding/stage_screens.dart';
import '../features/payments/beneficiaries_screen.dart';
import '../features/payments/transfer_screen.dart';
import '../features/products/loan_screen.dart';
import '../features/products/loans_screen.dart';
import '../features/products/susu_screen.dart';
import '../features/security/security_screen.dart';
import '../features/welcome/welcome_screen.dart';

abstract final class Routes {
  static const splash = '/splash';
  static const welcome = '/';
  static const signIn = '/sign-in';
  static const verify = '/sign-in/verify';
  static const forgotPassword = '/sign-in/forgot-password';
  static const activate = '/activate';
  static const signUp = '/sign-up';
  static const setup = '/setup';
  static const home = '/home';
  static const transfer = '/home/transfer';
  static const beneficiaries = '/home/beneficiaries';
  static const loans = '/home/loans';
  static const susu = '/home/susu';
  static const security = '/home/security';

  static String account(String id) => '/home/accounts/$id';

  static String loan(String id) => '/home/loans/$id';

  static String susuPlan(String id) => '/home/susu/$id';

  /// A stage of signing up (`PERSONAL_DETAILS`, `ADDRESS`, ...).
  static String signUpStage(String code) => '/home/sign-up/${code.toLowerCase().replaceAll('_', '-')}';

  /// What signed-out visitors may open.
  static const public = {welcome, signIn, forgotPassword, activate, signUp};
}

/// Signed-out visitors may browse the public screens; every other state is pinned to its screen, and a signed-in
/// customer stays within the app's home.
String? redirectFor(SessionState state, String location) {
  final target = switch (state) {
    SessionRestoring() => Routes.splash,
    SignedOut() => Routes.public.contains(location) ? location : Routes.welcome,
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
        GoRoute(path: Routes.forgotPassword, builder: (_, _) => const ForgotPasswordScreen()),
        GoRoute(path: Routes.activate, builder: (_, _) => const ActivationScreen()),
        GoRoute(path: Routes.signUp, builder: (_, _) => const SignUpScreen()),
        GoRoute(path: Routes.setup, builder: (_, _) => const SetupScreen()),
        GoRoute(path: Routes.home, builder: (_, _) => const HomeScreen()),
        GoRoute(path: Routes.transfer, builder: (_, state) => TransferScreen(fromAccountId: state.uri.queryParameters['from'])),
        GoRoute(path: Routes.beneficiaries, builder: (_, _) => const BeneficiariesScreen()),
        GoRoute(path: Routes.loans, builder: (_, _) => const LoansScreen()),
        GoRoute(path: '/home/loans/:id', builder: (_, state) => LoanScreen(loanId: state.pathParameters['id']!)),
        GoRoute(path: Routes.susu, builder: (_, _) => const SusuPlansScreen()),
        GoRoute(path: '/home/susu/:id', builder: (_, state) => SusuPlanScreen(planId: state.pathParameters['id']!)),
        GoRoute(path: Routes.security, builder: (_, _) => const SecurityScreen()),
        GoRoute(path: '/home/accounts/:id', builder: (_, state) => AccountScreen(accountId: state.pathParameters['id']!)),
        GoRoute(path: '/home/sign-up/:stage', builder: (_, state) => SignUpStageScreen(stage: state.pathParameters['stage']!)),
      ],
    );
