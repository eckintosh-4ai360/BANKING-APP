import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../api/models.dart';
import '../../app/providers.dart';
import '../../app/router.dart';

class MoreTab extends ConsumerWidget {
  const MoreTab({super.key, required this.profile});

  final CustomerProfile profile;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final branding = ref.watch(brandingProvider).requireValue;
    return ListView(
      padding: const EdgeInsets.all(Gaps.md),
      children: [
        ListTile(
          leading: const CircleAvatar(child: Icon(Icons.person_outline)),
          title: Text(profile.displayName),
          subtitle: Text('Customer number ${profile.customerNumber}\n${profile.phoneNumber}'),
          isThreeLine: true,
        ),
        if (profile.pinLocked)
          const Padding(
            padding: EdgeInsets.symmetric(vertical: Gaps.sm),
            child: NoticeBanner('Your transaction PIN is locked. Reset it in Security to make payments.', error: true),
          ),
        const Divider(),
        FeatureTile(icon: Icons.request_quote_outlined, title: 'Loans', onTap: () => context.push(Routes.loans)),
        FeatureTile(icon: Icons.savings_outlined, title: 'Susu', onTap: () => context.push(Routes.susu)),
        FeatureTile(
          icon: Icons.shield_outlined,
          title: 'Security',
          subtitle: 'PIN, password, devices and text alerts',
          onTap: () => context.push(Routes.security),
        ),
        if (branding.supportPhone != null || branding.supportEmail != null) ...[
          const Divider(),
          ListTile(title: Text('Help from ${branding.displayName}')),
          if (branding.supportPhone != null)
            ListTile(leading: const Icon(Icons.phone_outlined), title: Text(branding.supportPhone!)),
          if (branding.supportEmail != null)
            ListTile(leading: const Icon(Icons.email_outlined), title: Text(branding.supportEmail!)),
        ],
        const SizedBox(height: Gaps.lg),
        PrimaryButton(label: 'Sign out', outlined: true, onPressed: () => ref.read(sessionProvider).signOut()),
      ],
    );
  }
}
