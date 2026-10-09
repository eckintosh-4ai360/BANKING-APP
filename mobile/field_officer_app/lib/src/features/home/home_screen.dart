import 'package:banking_api/banking_api.dart';
import 'package:banking_core/banking_core.dart';
import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/providers.dart';
import '../../app/router.dart';
import '../../offline/field_queue.dart';
import '../../offline/sync_engine.dart';

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
            tooltip: 'Collections and visits',
            icon: const Icon(Icons.receipt_long_outlined),
            onPressed: () => context.go(Routes.queue),
          ),
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

class _Home extends ConsumerWidget {
  const _Home({required this.profile});

  final StaffProfile profile;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final text = Theme.of(context).textTheme;
    final customers = ref.watch(customersProvider).value ?? const [];
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
        const SizedBox(height: Gaps.md),
        const SyncCard(),
        const SizedBox(height: Gaps.lg),
        Text('Your customers', style: text.titleMedium),
        const SizedBox(height: Gaps.sm),
        if (customers.isEmpty)
          const Text('Sync once to load the customers assigned to you.')
        else
          for (final customer in customers)
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: const Icon(Icons.person_outline),
              title: Text(customer.displayName),
              subtitle: Text(customer.customerNumber),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => context.go(Routes.customer(customer.customerId)),
            ),
      ],
    );
  }
}

/// What is waiting on the phone, when it last reached the server, and a button to sync now.
class SyncCard extends ConsumerStatefulWidget {
  const SyncCard({super.key});

  @override
  ConsumerState<SyncCard> createState() => _SyncCardState();
}

class _SyncCardState extends ConsumerState<SyncCard> {
  bool _syncing = false;
  SyncReport? _last;

  Future<void> _sync() async {
    setState(() => _syncing = true);
    final report = await ref.read(syncEngineProvider).sync();
    if (mounted) {
      setState(() {
        _syncing = false;
        _last = report;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    final summary = ref.watch(queueSummaryProvider).value;
    final officer = summary?.officer;
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(Gaps.md),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(_waiting(summary), style: text.titleMedium),
            const SizedBox(height: Gaps.xs),
            Text(summary?.lastSyncedAt == null ? 'Not synced yet' : 'Last synced ${_time(summary!.lastSyncedAt!)}', style: text.bodySmall),
            if (officer != null) ...[
              const SizedBox(height: Gaps.xs),
              Text('Cash you carry (as of the last sync): ${officer.currency} ${officer.cashBalance}', style: text.bodySmall),
            ],
            if (summary != null && summary.needsAttention > 0) ...[
              const SizedBox(height: Gaps.sm),
              Text(
                '${summary.needsAttention} not accepted by the server: see Collections and visits.',
                style: text.bodyMedium?.copyWith(color: Theme.of(context).colorScheme.error),
              ),
            ],
            if (_last != null) ...[
              const SizedBox(height: Gaps.sm),
              Text(_report(_last!), style: text.bodyMedium),
            ],
            const SizedBox(height: Gaps.sm),
            FilledButton.icon(
              onPressed: _syncing ? null : _sync,
              icon: _syncing
                  ? const SizedBox.square(dimension: 16, child: CircularProgressIndicator(strokeWidth: 2))
                  : const Icon(Icons.sync),
              label: const Text('Sync now'),
            ),
          ],
        ),
      ),
    );
  }

  static String _waiting(QueueSummary? summary) {
    if (summary == null || (summary.pendingCollections == 0 && summary.pendingVisits == 0)) {
      return 'Everything is synced';
    }
    final currency = summary.officer == null ? '' : '${summary.officer!.currency} ';
    final parts = [
      if (summary.pendingCollections > 0)
        '${summary.pendingCollections} ${summary.pendingCollections == 1 ? 'collection' : 'collections'} '
            '($currency${summary.pendingAmount})',
      if (summary.pendingVisits > 0) '${summary.pendingVisits} ${summary.pendingVisits == 1 ? 'visit' : 'visits'}',
    ];
    return '${parts.join(' and ')} waiting to sync';
  }

  static String _report(SyncReport report) {
    if (report.deviceRevoked) {
      return 'This phone was revoked by a supervisor. Nothing more is sent from it; speak to your branch.';
    }
    if (!report.ok) {
      return 'Could not sync: ${report.error} Your collections are safe on the phone; try again later.';
    }
    final notAccepted = report.rejected + report.conflicts;
    return report.sent == 0
        ? 'Synced. Nothing was waiting.'
        : 'Synced: ${report.accepted} accepted${notAccepted > 0 ? ', $notAccepted not accepted' : ''}.';
  }

  static String _time(DateTime time) {
    final local = time.toLocal();
    String two(int value) => value.toString().padLeft(2, '0');
    return '${local.year}-${two(local.month)}-${two(local.day)} ${two(local.hour)}:${two(local.minute)}';
  }
}
