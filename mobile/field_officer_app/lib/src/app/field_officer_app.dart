import 'dart:async';

import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'providers.dart';
import 'router.dart';

/// Unattended devices in the field are signed out after this long without interaction.
const inactivityTimeout = Duration(minutes: 10);

class FieldOfficerApp extends ConsumerStatefulWidget {
  const FieldOfficerApp({super.key});

  @override
  ConsumerState<FieldOfficerApp> createState() => _FieldOfficerAppState();
}

class _FieldOfficerAppState extends ConsumerState<FieldOfficerApp> {
  late final GoRouter _router = createRouter(ref.read(sessionProvider));

  @override
  void dispose() {
    _router.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp.router(
      title: 'Field Officer',
      theme: BankingTheme.light(),
      darkTheme: BankingTheme.dark(),
      routerConfig: _router,
      builder: (context, child) => Consumer(
        builder: (context, ref, _) {
          final state = ref.watch(sessionStateProvider);
          final active = state is! SignedOut && state is! SessionRestoring;
          return InactivityGuard(
            timeout: inactivityTimeout,
            enabled: active,
            onTimeout: () => unawaited(ref.read(sessionProvider).signOut(notice: 'You were signed out after a period of inactivity.')),
            child: child ?? const SizedBox.shrink(),
          );
        },
      ),
    );
  }
}
