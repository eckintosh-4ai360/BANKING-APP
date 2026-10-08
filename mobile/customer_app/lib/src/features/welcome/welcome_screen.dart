import 'package:banking_api/banking_api.dart';
import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/providers.dart';
import '../../app/router.dart';

class WelcomeScreen extends ConsumerWidget {
  const WelcomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final branding = ref.watch(brandingProvider).requireValue;
    final state = ref.watch(sessionStateProvider);
    final notice = state is SignedOut ? state.notice : null;
    final available = branding.isEnabled(mobileAppFeature);

    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(Gaps.lg),
          children: [
            const SizedBox(height: Gaps.xl),
            InstitutionHeader(name: branding.displayName, logoUrl: branding.logoUrl, subtitle: 'Mobile banking'),
            const SizedBox(height: Gaps.xl),
            if (notice != null) ...[NoticeBanner(notice), const SizedBox(height: Gaps.md)],
            if (available)
              PrimaryButton(label: 'Sign in', onPressed: () async => context.go(Routes.signIn))
            else
              NoticeBanner('Mobile banking is not yet available for ${branding.displayName}. Please visit a branch or contact us.'),
            const SizedBox(height: Gaps.lg),
            _SupportContacts(branding: branding),
          ],
        ),
      ),
    );
  }
}

class _SupportContacts extends StatelessWidget {
  const _SupportContacts({required this.branding});

  final InstitutionBranding branding;

  @override
  Widget build(BuildContext context) {
    if (branding.supportPhone == null && branding.supportEmail == null) {
      return const SizedBox.shrink();
    }
    return Card(
      child: Column(
        children: [
          const ListTile(title: Text('Need help?')),
          if (branding.supportPhone != null)
            ListTile(leading: const Icon(Icons.phone_outlined), title: Text(branding.supportPhone!)),
          if (branding.supportEmail != null)
            ListTile(leading: const Icon(Icons.email_outlined), title: Text(branding.supportEmail!)),
        ],
      ),
    );
  }
}
