import 'package:banking_api/banking_api.dart';
import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(profileProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Field Officer'),
        actions: [
          IconButton(
            tooltip: 'Sign out',
            icon: const Icon(Icons.logout),
            onPressed: () => ref.read(sessionProvider).signOut(),
          ),
        ],
      ),
      body: SafeArea(
        child: switch (profile) {
          AsyncData(:final value) => _Home(profile: value),
          AsyncError(:final error) => ErrorView(
              message: error is ApiException ? error.message : 'Your profile could not be loaded.',
              onRetry: () => ref.invalidate(profileProvider),
            ),
          _ => const LoadingView(),
        },
      ),
    );
  }
}

class _Home extends StatelessWidget {
  const _Home({required this.profile});

  final StaffProfile profile;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return ListView(
      padding: const EdgeInsets.all(Gaps.md),
      children: [
        Text('Hello, ${profile.firstName}', style: text.headlineSmall),
        Text(profile.institutionName, style: text.bodyLarge),
        const SizedBox(height: Gaps.sm),
        Wrap(
          spacing: Gaps.sm,
          runSpacing: Gaps.xs,
          children: [
            for (final role in profile.roles) Chip(label: Text(role.name)),
            Chip(
              avatar: const Icon(Icons.location_on_outlined, size: 18),
              label: Text(profile.allBranchesAccess ? 'All branches' : 'Home branch'),
            ),
          ],
        ),
        const SizedBox(height: Gaps.lg),
        Text('Work', style: text.titleMedium),
        const SizedBox(height: Gaps.sm),
        // The field workflows ship with field operations (offline-capable collections, visits and sync). Until
        // then they are listed but disabled, so the app never offers something it can't do.
        const FeatureTile(icon: Icons.person_add_alt_1_outlined, title: 'Customer onboarding', unavailableReason: 'Coming in a later release'),
        const FeatureTile(icon: Icons.savings_outlined, title: 'Collections', unavailableReason: 'Coming in a later release'),
        const FeatureTile(icon: Icons.route_outlined, title: 'Visits', unavailableReason: 'Coming in a later release'),
        const FeatureTile(icon: Icons.sync_outlined, title: 'Sync status', unavailableReason: 'Coming in a later release'),
      ],
    );
  }
}
