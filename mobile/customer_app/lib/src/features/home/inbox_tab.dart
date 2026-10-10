import 'package:banking_ui/banking_ui.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../api/models.dart';
import '../../app/providers.dart';
import '../../widgets/common.dart';

/// Alerts for money in and out, security notices, loan reminders and messages about the account, newest first.
class InboxTab extends ConsumerWidget {
  const InboxTab({super.key});

  Future<void> _open(WidgetRef ref, CustomerNotification notification) async {
    if (!notification.read) {
      await ref.read(customerApiProvider).markRead(notification.id);
      ref
        ..invalidate(notificationsProvider)
        ..invalidate(unreadProvider);
    }
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final page = ref.watch(notificationsProvider);
    return RefreshIndicator(
      onRefresh: () {
        ref.invalidate(unreadProvider);
        return ref.refresh(notificationsProvider.future);
      },
      child: AsyncBody(
        value: page,
        onRetry: () => ref.invalidate(notificationsProvider),
        builder: (page) => page.items.isEmpty
            ? ListView(children: const [SizedBox(height: Gaps.xl), Center(child: Text('Nothing here yet.'))])
            : ListView(
                padding: const EdgeInsets.symmetric(vertical: Gaps.sm),
                children: [
                  if (page.items.any((notification) => !notification.read))
                    Align(
                      alignment: Alignment.centerRight,
                      child: TextButton(
                        onPressed: () async {
                          await ref.read(customerApiProvider).markAllRead();
                          ref
                            ..invalidate(notificationsProvider)
                            ..invalidate(unreadProvider);
                        },
                        child: const Text('Mark all read'),
                      ),
                    ),
                  for (final notification in page.items)
                    ListTile(
                      leading: Icon(_icon(notification.category)),
                      title: Text(
                        notification.title,
                        style: notification.read ? null : const TextStyle(fontWeight: FontWeight.bold),
                      ),
                      subtitle: Text('${notification.body}\n${formatDateTime(notification.createdAt)}'),
                      isThreeLine: true,
                      onTap: () => _open(ref, notification),
                    ),
                ],
              ),
      ),
    );
  }

  static IconData _icon(String category) => switch (category) {
        'TRANSACTION' => Icons.swap_vert,
        'SECURITY' => Icons.shield_outlined,
        'LOAN' => Icons.request_quote_outlined,
        'ACCOUNT' => Icons.account_balance_outlined,
        _ => Icons.notifications_outlined,
      };
}
