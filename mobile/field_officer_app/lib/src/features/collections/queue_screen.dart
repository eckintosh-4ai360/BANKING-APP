import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/providers.dart';
import '../../offline/field_database.dart';

/// Everything recorded on the phone and what the server said about it.
class QueueScreen extends ConsumerWidget {
  const QueueScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final collections = ref.watch(collectionsProvider).value ?? const [];
    final visits = ref.watch(visitsProvider).value ?? const [];
    final text = Theme.of(context).textTheme;
    return Scaffold(
      appBar: AppBar(title: const Text('Collections and visits')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(Gaps.md),
          children: [
            Text('Collections', style: text.titleMedium),
            if (collections.isEmpty) const Text('No collections recorded on this phone.'),
            for (final collection in collections)
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: CircleAvatar(child: Text('${collection.sequenceNo}')),
                title: Text('${collection.currency} ${collection.amount} · ${collection.customerName}'),
                subtitle: Text([
                  collection.accountNumber,
                  if (collection.transactionReference != null) collection.transactionReference!,
                  if (collection.serverMessage != null) collection.serverMessage!,
                ].join('\n')),
                isThreeLine: collection.serverMessage != null || collection.transactionReference != null,
                trailing: StatusChip(status: collection.status),
              ),
            const SizedBox(height: Gaps.lg),
            Text('Visits', style: text.titleMedium),
            if (visits.isEmpty) const Text('No visits recorded on this phone.'),
            for (final visit in visits)
              ListTile(
                contentPadding: EdgeInsets.zero,
                leading: const Icon(Icons.route_outlined),
                title: Text(visit.customerName),
                subtitle: Text('${visit.purpose.toLowerCase().replaceAll('_', ' ')} · ${visit.outcome.toLowerCase().replaceAll('_', ' ')}'),
                trailing: StatusChip(status: visit.status),
              ),
          ],
        ),
      ),
    );
  }
}

class StatusChip extends StatelessWidget {
  const StatusChip({super.key, required this.status});

  final QueueStatus status;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final (label, color) = switch (status) {
      QueueStatus.queued => ('Waiting', scheme.outline),
      QueueStatus.sent => ('Sending', scheme.outline),
      QueueStatus.accepted => ('Accepted', scheme.primary),
      QueueStatus.rejected => ('Rejected', scheme.error),
      QueueStatus.conflict => ('Conflict', scheme.error),
    };
    return Chip(label: Text(label), side: BorderSide(color: color), labelStyle: TextStyle(color: color));
  }
}
