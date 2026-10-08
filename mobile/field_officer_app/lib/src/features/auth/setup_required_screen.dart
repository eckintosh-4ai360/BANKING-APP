import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';

/// The account must enrol an authenticator first. Enrolment happens in the web console, where the setup key can
/// be shown safely on a second screen while the phone scans or types it.
class SetupRequiredScreen extends ConsumerWidget {
  const SetupRequiredScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      appBar: AppBar(title: const Text('Two-step verification required')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(Gaps.lg),
          children: [
            const Icon(Icons.verified_user_outlined, size: 56),
            const SizedBox(height: Gaps.md),
            const Text(
              'Your institution requires two-step verification for your account. Sign in to the institution web '
              'console, open Account security and set up an authenticator app. Then sign in here again.',
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: Gaps.lg),
            PrimaryButton(label: 'Sign out', onPressed: () => ref.read(sessionProvider).signOut()),
          ],
        ),
      ),
    );
  }
}
