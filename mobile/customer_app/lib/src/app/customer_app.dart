import 'dart:async';

import 'package:banking_api/banking_api.dart';
import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'providers.dart';
import 'router.dart';

/// Customer sessions end after this long without interaction.
const inactivityTimeout = Duration(minutes: 5);

/// Loads the institution's branding first, then runs the app in its colours.
class CustomerApp extends ConsumerWidget {
  const CustomerApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return switch (ref.watch(brandingProvider)) {
      AsyncData(:final value) => _BrandedApp(branding: value),
      AsyncError(:final error) => MaterialApp(
          theme: BankingTheme.light(),
          home: Scaffold(
            body: error is MissingInstitutionError
                ? ErrorView(message: error.toString())
                : ErrorView(
                    message: error is ApiException ? error.message : 'The app could not start.',
                    onRetry: () => ref.invalidate(brandingProvider),
                  ),
          ),
        ),
      _ => MaterialApp(theme: BankingTheme.light(), home: const Scaffold(body: LoadingView())),
    };
  }
}

class _BrandedApp extends ConsumerStatefulWidget {
  const _BrandedApp({required this.branding});

  final InstitutionBranding branding;

  @override
  ConsumerState<_BrandedApp> createState() => _BrandedAppState();
}

class _BrandedAppState extends ConsumerState<_BrandedApp> {
  late final GoRouter _router = createRouter(ref.read(sessionProvider));

  @override
  void dispose() {
    _router.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: widget.branding.displayName,
      theme: BankingTheme.light(widget.branding),
      darkTheme: BankingTheme.dark(widget.branding),
      routerConfig: _router,
      builder: (context, child) => Consumer(
        builder: (context, ref, _) {
          final state = ref.watch(sessionStateProvider);
          return InactivityGuard(
            timeout: inactivityTimeout,
            enabled: state is! SignedOut && state is! SessionRestoring,
            onTimeout: () => unawaited(ref.read(sessionProvider).signOut(notice: 'You were signed out after a period of inactivity.')),
            child: child ?? const SizedBox.shrink(),
          );
        },
      ),
    );
  }
}
