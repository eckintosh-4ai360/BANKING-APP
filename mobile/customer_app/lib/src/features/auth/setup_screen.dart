import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';

/// The account needs a step the app doesn't offer yet (a new password or two-step verification).
class SetupScreen extends ConsumerWidget {
  const SetupScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final branding = ref.watch(brandingProvider).requireValue;
    final contact = branding.supportPhone ?? branding.supportEmail;
    return Scaffold(
      appBar: AppBar(title: const Text('Finish setting up your account')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(Gaps.lg),
          children: [
            NoticeBanner(
              'Your account needs to finish setting up before you can use mobile banking.'
              '${contact == null ? '' : ' Please contact ${branding.displayName} on $contact.'}',
            ),
            const SizedBox(height: Gaps.lg),
            PrimaryButton(label: 'Sign out', onPressed: () => ref.read(sessionProvider).signOut()),
          ],
        ),
      ),
    );
  }
}
